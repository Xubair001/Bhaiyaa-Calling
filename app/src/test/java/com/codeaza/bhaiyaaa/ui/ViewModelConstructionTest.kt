package com.codeaza.bhaiyaaa.ui

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewmodel.MutableCreationExtras
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.ui.assistant.AssistantViewModel
import com.codeaza.bhaiyaaa.ui.models.ModelManagerViewModel
import com.codeaza.bhaiyaaa.ui.prayer.PrayerViewModel
import com.codeaza.bhaiyaaa.ui.recordings.VoiceRecordingViewModel
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Every view model can actually be built the way the framework builds it.
 *
 * ## Why this exists
 *
 * `viewModel()` does not call a constructor - it looks one up by reflection.
 * `AndroidViewModelFactory` wants `<init>(Application)` exactly; the
 * saved-state factory wants `<init>(Application, SavedStateHandle)`. Neither
 * is checked at compile time, so a view model can be perfectly valid Kotlin,
 * pass every test, and crash the app the instant a screen asks for it.
 *
 * That is not hypothetical. Adding injectable dispatchers to [PrayerViewModel]
 * as default arguments removed its one-argument JVM constructor - Kotlin
 * defaults generate the full constructor and a synthetic bridge, not an
 * overload - and the app crashed on launch, every time. Every unit test still
 * passed, because the tests build view models directly through their own
 * factory and never touch the reflective path.
 *
 * So this test goes through `ViewModelProvider`, exactly as a screen does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ViewModelConstructionTest {

    private val store = ViewModelStore()

    @After
    fun tearDown() = store.clear()

    /**
     * Builds through the real factory, with the same extras Compose supplies.
     */
    private fun <T : ViewModel> build(kclass: Class<T>): T {
        val application = ApplicationProvider.getApplicationContext<Application>()
        val extras = MutableCreationExtras().apply {
            set(ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY, application)
        }
        return ViewModelProvider.create(
            store,
            ViewModelProvider.AndroidViewModelFactory(),
            extras
        )[kclass]
    }

    @Test
    fun `SukoonViewModel can be built by the framework`() {
        assertThat(build(SukoonViewModel::class.java)).isNotNull()
    }

    @Test
    fun `PrayerViewModel can be built by the framework`() {
        // The one that actually broke. Its dispatchers are injectable for
        // tests, which must not cost the app its launch.
        assertThat(build(PrayerViewModel::class.java)).isNotNull()
    }

    @Test
    fun `ModelManagerViewModel can be built by the framework`() {
        assertThat(build(ModelManagerViewModel::class.java)).isNotNull()
    }

    @Test
    fun `VoiceRecordingViewModel can be built by the framework`() {
        assertThat(build(VoiceRecordingViewModel::class.java)).isNotNull()
    }

    @Test
    fun `every AndroidViewModel keeps the constructor the factory looks up`() {
        // The direct statement of the invariant, so the reason survives even
        // if the tests above are ever changed to build things differently.
        listOf(
            SukoonViewModel::class.java,
            PrayerViewModel::class.java,
            ModelManagerViewModel::class.java,
            VoiceRecordingViewModel::class.java
        ).forEach { type ->
            val found = runCatching { type.getConstructor(Application::class.java) }
            assertThat(found.isSuccess).isTrue()
        }

        // The assistant takes a SavedStateHandle so its conversation can
        // survive process death, which is its own valid signature - the
        // saved-state factory looks for exactly this pair.
        assertThat(
            runCatching {
                AssistantViewModel::class.java
                    .getConstructor(Application::class.java, SavedStateHandle::class.java)
            }.isSuccess
        ).isTrue()
    }
}
