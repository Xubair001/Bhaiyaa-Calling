package com.codeaza.bhaiyaaa.prayer

import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * When the adhan is allowed to sound.
 *
 * Audio that starts on its own is the most intrusive thing this app does, so
 * every reason not to play is one decision, in one pure function, with a test
 * for each. "Does not play when disabled", "does not play twice" and "a new
 * day is a new chance" are properties here rather than behaviours somebody has
 * to sit and wait for.
 */
class AdhanPlaybackTest {

    private val karachi: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        Calendar.getInstance(karachi).apply {
            clear()
            set(year, month, day, hour, minute, 0)
        }.timeInMillis

    private fun key(prayer: Prayer, millis: Long) =
        AdhanPlayback.dayKey(prayer, millis, karachi)

    @Test
    fun `it plays when it is switched on and nothing has played yet`() {
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = true,
                lastPlayedKey = null,
                requestKey = key(Prayer.ASR, at(2025, Calendar.AUGUST, 27, 16, 0)),
                minutesLate = 0
            )
        ).isTrue()
    }

    @Test
    fun `it never plays when the user has switched it off`() {
        // The alarm may have been armed before the preference changed, so the
        // preference is checked here rather than trusted from then.
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = false,
                prayerEnabled = true,
                lastPlayedKey = null,
                requestKey = key(Prayer.ASR, at(2025, Calendar.AUGUST, 27, 16, 0)),
                minutesLate = 0
            )
        ).isFalse()
    }

    @Test
    fun `it does not play for a prayer that is switched off`() {
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = false,
                lastPlayedKey = null,
                requestKey = key(Prayer.FAJR, at(2025, Calendar.AUGUST, 27, 5, 0)),
                minutesLate = 0
            )
        ).isFalse()
    }

    @Test
    fun `it does not play the same prayer twice in one day`() {
        // Doze, a reboot re-arming, and a duplicated broadcast can each deliver
        // the same alarm again.
        val moment = at(2025, Calendar.AUGUST, 27, 16, 0)
        val request = key(Prayer.ASR, moment)
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = true,
                lastPlayedKey = request,
                requestKey = request,
                minutesLate = 0
            )
        ).isFalse()
    }

    @Test
    fun `tomorrow's prayer plays even though today's already did`() {
        val today = key(Prayer.FAJR, at(2025, Calendar.AUGUST, 27, 5, 0))
        val tomorrow = key(Prayer.FAJR, at(2025, Calendar.AUGUST, 28, 5, 0))
        assertThat(today).isNotEqualTo(tomorrow)
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = true,
                lastPlayedKey = today,
                requestKey = tomorrow,
                minutesLate = 0
            )
        ).isTrue()
    }

    @Test
    fun `a different prayer on the same day still plays`() {
        val moment = at(2025, Calendar.AUGUST, 27, 16, 0)
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = true,
                lastPlayedKey = key(Prayer.DHUHR, moment),
                requestKey = key(Prayer.ASR, moment),
                minutesLate = 0
            )
        ).isTrue()
    }

    @Test
    fun `an alarm held back by Doze until long after the prayer stays silent`() {
        // An adhan forty minutes late is not a call to prayer, it is a
        // confusing noise.
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = true,
                lastPlayedKey = null,
                requestKey = key(Prayer.ISHA, at(2025, Calendar.AUGUST, 27, 20, 0)),
                minutesLate = AdhanPlayback.MAX_LATENESS_MINUTES + 1
            )
        ).isFalse()
    }

    @Test
    fun `a few minutes late is still on time enough to sound`() {
        assertThat(
            AdhanPlayback.shouldPlay(
                adhanEnabled = true,
                prayerEnabled = true,
                lastPlayedKey = null,
                requestKey = key(Prayer.ISHA, at(2025, Calendar.AUGUST, 27, 20, 0)),
                minutesLate = 2
            )
        ).isTrue()
    }

    @Test
    fun `the day key follows the configured zone, not the device's`() {
        // One in the morning in Karachi is still the previous evening in
        // London, and the user's chosen zone is what "today" has to mean -
        // otherwise a traveller with a zone override gets the same adhan twice
        // or not at all across the boundary.
        val moment = at(2025, Calendar.AUGUST, 28, 1, 0)
        val karachiKey = AdhanPlayback.dayKey(Prayer.FAJR, moment, karachi)
        val londonKey = AdhanPlayback.dayKey(
            Prayer.FAJR, moment, TimeZone.getTimeZone("Europe/London")
        )
        assertThat(karachiKey).isEqualTo("FAJR@2025-08-28")
        assertThat(londonKey).isEqualTo("FAJR@2025-08-27")
    }
}
