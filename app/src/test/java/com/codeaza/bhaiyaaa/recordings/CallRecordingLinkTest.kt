package com.codeaza.bhaiyaaa.recordings

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.db.AppDatabase
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingEntity
import com.codeaza.bhaiyaaa.data.db.entity.VoiceRecordingSource
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Filing a recording against a call.
 *
 * Sukoon cannot capture call audio - Android reserves that for privileged,
 * pre-installed apps - so this link is the achievable half of the same need: a
 * note made after the call, or a file the phone's own dialer produced, kept
 * with the call it belongs to.
 *
 * The link is deliberately not a foreign key, and that decision is what these
 * tests mostly exist to pin down.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CallRecordingLinkTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun recording(
        fileName: String,
        callId: Long? = null,
        createdAt: Long = 1L
    ) = VoiceRecordingEntity(
        label = fileName,
        fileName = fileName,
        durationMillis = 1_000,
        createdAt = createdAt,
        source = VoiceRecordingSource.RECORDED.storageValue,
        callId = callId
    )

    @Test
    fun `a call's notes are the ones filed against it`() = runTest {
        db.voiceRecordingDao().insert(recording("a.m4a", callId = 10L))
        db.voiceRecordingDao().insert(recording("b.m4a", callId = 20L))
        db.voiceRecordingDao().insert(recording("c.m4a", callId = 10L))

        val forCall = db.voiceRecordingDao().observeForCall(10L).first()

        assertThat(forCall.map { it.fileName }).containsExactly("a.m4a", "c.m4a")
    }

    @Test
    fun `a call's notes read in the order they were made`() = runTest {
        db.voiceRecordingDao().insert(recording("second.m4a", callId = 10L, createdAt = 200L))
        db.voiceRecordingDao().insert(recording("first.m4a", callId = 10L, createdAt = 100L))

        val forCall = db.voiceRecordingDao().observeForCall(10L).first()

        assertThat(forCall.map { it.fileName })
            .containsExactly("first.m4a", "second.m4a").inOrder()
    }

    @Test
    fun `a standalone recording belongs to no call`() = runTest {
        // The adhan is a recording too, and it must not appear under a call.
        db.voiceRecordingDao().insert(recording("adhan.m4a", callId = null))

        assertThat(db.voiceRecordingDao().observeForCall(10L).first()).isEmpty()
        assertThat(db.voiceRecordingDao().allOnce()).hasSize(1)
    }

    @Test
    fun `a note survives the call it was attached to disappearing`() = runTest {
        // Clearing the phone's call log removes the call row on the next sync.
        // A note the user recorded by hand is theirs, and losing it because the
        // call log was tidied would be the app deleting their data - which is
        // exactly why this link is not a foreign key with a cascade.
        db.voiceRecordingDao().insert(recording("note.m4a", callId = 999L))

        val stored = db.voiceRecordingDao().allOnce().single()

        assertThat(stored.callId).isEqualTo(999L)
        assertThat(db.callRecordDao().findById(999L)).isNull()
    }

    @Test
    fun `a recording can be filed against a call that exists`() = runTest {
        db.voiceRecordingDao().insert(recording("note.m4a", callId = 7L))

        val forCall = db.voiceRecordingDao().observeForCall(7L).first()

        assertThat(forCall).hasSize(1)
        assertThat(forCall.single().source)
            .isEqualTo(VoiceRecordingSource.RECORDED.storageValue)
    }
}
