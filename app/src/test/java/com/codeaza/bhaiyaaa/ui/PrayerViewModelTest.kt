package com.codeaza.bhaiyaaa.ui

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.codeaza.bhaiyaaa.domain.model.PrayerMode
import com.codeaza.bhaiyaaa.domain.model.SilenceWindow
import com.codeaza.bhaiyaaa.prayer.PrayerTimeCalculator
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The instant-feedback contract for changing a prayer time.
 *
 * The brief's rule was: the UI updates immediately, persistence happens behind
 * it, and a failed write rolls back visibly rather than leaving the screen
 * showing something the database does not hold. All three are asserted here,
 * because "it feels fast" is not something a test can check and "the value is
 * on screen before the write returns" is.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrayerViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var db: AppDatabase

    /**
     * Owns the view models, so they can actually be cleared.
     *
     * A view model built with `new` keeps its `viewModelScope` running for the
     * life of the JVM: nothing cancels it. Those coroutines then resume on
     * whichever Main dispatcher the *next* test installs, hit the database
     * this one closed, and throw into a test that has nothing to do with them.
     * Going through a store means `clear()` cancels the scope, which is what
     * the framework does on a real screen anyway.
     */
    private val viewModelStore = ViewModelStore()

    @Before
    fun setUp() = runBlocking {
        Dispatchers.setMain(dispatcher)
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        )
            // Room's Flows emit on its own executors. Handing it the test
            // dispatcher is what lets a query and its emission complete
            // within the test rather than after it.
            .setQueryExecutor(dispatcher.asExecutor())
            .setTransactionExecutor(dispatcher.asExecutor())
            .allowMainThreadQueries()
            .build()
        AppDatabase.setInstanceForTest(db)
        db.prayerDao().insertIfAbsent(PrayerTimeCalculator.defaultPrayerRows())
    }

    @After
    fun tearDown() {
        // Order matters: cancel the view models before taking away the
        // database and the Main dispatcher they are using.
        viewModelStore.clear()
        AppDatabase.setInstanceForTest(null)
        if (db.isOpen) db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.viewModel(): PrayerViewModel {
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PrayerViewModel(
                application = ApplicationProvider.getApplicationContext<Application>(),
                computeDispatcher = dispatcher,
                ioDispatcher = dispatcher
            ) as T
        }
        val vm = ViewModelProvider(viewModelStore, factory)[PrayerViewModel::class.java]
        // The state flows are WhileSubscribed, which is right for a screen and
        // means a test has to behave like one.
        backgroundScope.launch { vm.prayers.collect {} }
        backgroundScope.launch { vm.todayWindows.collect {} }
        return vm
    }

    private fun PrayerViewModel.timeFor(prayer: Prayer): Int? =
        prayers.value.firstOrNull { it.name == prayer.storageValue }?.manualMinutesFromMidnight

    @Test
    fun `a new time is on screen before the database is consulted again`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.savePrayerEdit(Prayer.ASR, 17 * 60 + 5, silenceMinutes = 20, startOffsetMinutes = -4)

        assertThat(vm.timeFor(Prayer.ASR)).isEqualTo(17 * 60 + 5)
    }

    @Test
    fun `the change reaches storage as well as the screen`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.savePrayerEdit(Prayer.MAGHRIB, 18 * 60 + 40, silenceMinutes = 18, startOffsetMinutes = -6)

        val row = requireNotNull(db.prayerDao().find(Prayer.MAGHRIB.storageValue))
        assertThat(row.manualMinutesFromMidnight).isEqualTo(18 * 60 + 40)
        assertThat(row.silenceMinutes).isEqualTo(18)
        assertThat(row.startOffsetMinutes).isEqualTo(-6)
    }

    @Test
    fun `an invalid time is corrected on its way through, not rejected afterwards`() = runTest(dispatcher) {
        val vm = viewModel()

        // A morning time offered for an afternoon prayer.
        vm.savePrayerEdit(Prayer.ASR, 4 * 60 + 30, silenceMinutes = 15, startOffsetMinutes = -3)

        // Corrected on screen as well as in storage, so the two never disagree.
        assertThat(vm.timeFor(Prayer.ASR)).isEqualTo(16 * 60 + 30)
        assertThat(db.prayerDao().find(Prayer.ASR.storageValue)?.manualMinutesFromMidnight)
            .isEqualTo(16 * 60 + 30)
    }

    @Test
    fun `switching a prayer off shows immediately`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.setPrayerEnabled(Prayer.FAJR, false)

        assertThat(vm.prayers.value.first { it.name == Prayer.FAJR.storageValue }.enabled)
            .isFalse()
        assertThat(db.prayerDao().find(Prayer.FAJR.storageValue)?.enabled).isFalse()
    }

    @Test
    fun `an out-of-range value settles rather than leaving the overlay stuck`() = runTest(dispatcher) {
        val vm = viewModel()

        // The DAO clamps these. If the optimistic copy did not clamp them the
        // same way, the stored row would never match the overlay, and the
        // overlay would mask the real value for the life of the screen.
        vm.savePrayerEdit(
            Prayer.DHUHR,
            minutesFromMidnight = 13 * 60,
            silenceMinutes = 10_000,
            startOffsetMinutes = -5_000
        )

        val stored = requireNotNull(db.prayerDao().find(Prayer.DHUHR.storageValue))
        val shown = vm.prayers.value.first { it.name == Prayer.DHUHR.storageValue }
        assertThat(shown).isEqualTo(stored)
    }

    @Test
    fun `a failed write rolls the screen back and says so`() = runTest(dispatcher) {
        val vm = viewModel()
        val before = vm.timeFor(Prayer.ISHA)

        // Storage that will not accept the write. Dropping the table rather
        // than closing the database, because Room reopens a closed one on the
        // next access - against a fresh, empty in-memory database, which would
        // have made the write appear to succeed.
        db.openHelper.writableDatabase.execSQL("DROP TABLE prayers")

        vm.savePrayerEdit(Prayer.ISHA, 21 * 60, silenceMinutes = 15, startOffsetMinutes = -3)

        // The optimistic value is withdrawn rather than left standing over a
        // database that does not hold it.
        assertThat(vm.timeFor(Prayer.ISHA)).isEqualTo(before)
        assertThat(vm.message.value).contains("didn't save")
    }

    @Test
    fun `today's windows follow an edit without being asked to`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.setEnabled(true)
        vm.setMode(PrayerMode.MANUAL)
        // Settings live in DataStore, which emits on its own dispatcher - so
        // the test waits for the setting to land rather than assuming it has.
        vm.settings.first { it.enabled && it.mode == PrayerMode.MANUAL }

        vm.savePrayerEdit(Prayer.DHUHR, 13 * 60, silenceMinutes = 15, startOffsetMinutes = -3)

        // Derived state, not recomputed state: nothing anywhere told the
        // windows to refresh, and they are correct anyway.
        val dhuhr = vm.todayWindows
            .first { windows -> windows.any { it.key == SilenceWindow.prayerKey(Prayer.DHUHR) } }
            .first { it.key == SilenceWindow.prayerKey(Prayer.DHUHR) }
        assertThat(dhuhr.isOverridden).isTrue()
    }
}
