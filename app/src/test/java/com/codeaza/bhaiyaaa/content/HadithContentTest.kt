package com.codeaza.bhaiyaaa.content

import androidx.test.core.app.ApplicationProvider
import com.codeaza.bhaiyaaa.data.content.HadithRepository
import com.codeaza.bhaiyaaa.domain.model.HadithGrade
import com.codeaza.bhaiyaaa.domain.model.Prayer
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Hadith content file itself.
 *
 * This is religious content, so the checks here are about integrity rather
 * than about the UI: every entry has to carry a source that can be looked up,
 * every id has to be unique so "don't repeat that one" survives a reordering
 * of the file, and every period has to have enough to draw from without any
 * entry being duplicated to pad a count.
 *
 * The file is meant to be edited without touching Kotlin, which is exactly why
 * it needs a test - a bad edit would otherwise reach a user before it reached
 * a compiler.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class HadithContentTest {

    private val repository = HadithRepository(ApplicationProvider.getApplicationContext())

    @Test
    fun `the content file parses`() = runTest {
        assertThat(repository.all()).isNotEmpty()
    }

    @Test
    fun `every entry carries a source that can be checked`() = runTest {
        repository.all().forEach { hadith ->
            assertThat(hadith.reference).isNotEmpty()
            assertThat(hadith.text).isNotEmpty()
            assertThat(hadith.id).isNotEmpty()
        }
    }

    @Test
    fun `every reference names a specific hadith number`() = runTest {
        // A collection name on its own is not a citation anyone can follow.
        // Three entries were dropped rather than shipped with a vague one, and
        // this is what stops a fourth being added.
        repository.all().forEach { hadith ->
            assertThat(hadith.reference).containsMatch("\\d")
        }
    }

    @Test
    fun `a weak grading cannot even be expressed`() = runTest {
        // Jami' at-Tirmidhi 586 sat in this file until it was checked and
        // found da'if. The type has no value for weak, so the content file
        // has no way to say it - a weak narration has to be left out rather
        // than shown with a label warning about itself.
        assertThat(HadithGrade.from("da'if")).isNull()
        assertThat(HadithGrade.entries.map { it.name })
            .containsNoneOf("DAIF", "DA_IF", "WEAK")

        val allowed = listOf(null, HadithGrade.SAHIH, HadithGrade.HASAN_SAHIH, HadithGrade.HASAN)
        repository.all().forEach { hadith ->
            assertThat(hadith.grade).isIn(allowed)
        }
    }

    @Test
    fun `ids are unique`() = runTest {
        // The no-immediate-repeat rule keys off the id, so a duplicate would
        // silently make two entries the same one.
        val all = repository.all()
        assertThat(all.map { it.id }.toSet()).hasSize(all.size)
    }

    @Test
    fun `no two entries carry the same text`() = runTest {
        // The brief was explicit: do not duplicate content to reach a number.
        val all = repository.all()
        assertThat(all.map { it.text }.toSet()).hasSize(all.size)
    }

    @Test
    fun `every prayer period has enough to draw from`() = runTest {
        Prayer.entries.forEach { prayer ->
            assertThat(repository.forPeriod(prayer).size).isAtLeast(MINIMUM_PER_PERIOD)
        }
    }

    @Test
    fun `each period leads with the narrations that name it`() = runTest {
        Prayer.entries.forEach { prayer ->
            val pool = repository.forPeriod(prayer)
            val specific = pool.takeWhile { it.isSpecificTo(prayer) }
            // Ordering is what lets a caller take only the pointed ones.
            assertThat(pool.count { it.isSpecificTo(prayer) }).isEqualTo(specific.size)
        }
    }

    @Test
    fun `a general narration is offered in every period`() = runTest {
        val general = repository.all().filter { it.prayers.isEmpty() }
        assertThat(general).isNotEmpty()
        Prayer.entries.forEach { prayer ->
            assertThat(general.all { it.appliesTo(prayer) }).isTrue()
        }
    }

    @Test
    fun `a prayer-specific narration is not offered in another period`() = runTest {
        val specific = repository.all().filter { it.prayers.isNotEmpty() }
        assertThat(specific).isNotEmpty()
        specific.forEach { hadith ->
            Prayer.entries.filterNot { it in hadith.prayers }.forEach { other ->
                assertThat(hadith.appliesTo(other)).isFalse()
            }
        }
    }

    @Test
    fun `entries outside Bukhari and Muslim state their grading`() = runTest {
        // Inclusion in the two Sahih collections is itself the grading; every
        // other collection needs the reader to be told what it was graded, or
        // the app is presenting a claim as though it were settled.
        repository.all()
            .filterNot { it.reference.contains("Bukhari") || it.reference.contains("Muslim") }
            .forEach { hadith ->
                assertThat(hadith.grade).isNotNull()
            }
    }

    @Test
    fun `entries stay short enough to read on a card`() = runTest {
        repository.all().forEach { hadith ->
            assertThat(hadith.text.length).isAtMost(MAX_TEXT_LENGTH)
        }
    }

    private companion object {
        /**
         * The brief asked for roughly 20-25 per period. Asserted as a floor
         * rather than a range: more is fine, and the point of the check is
         * that no period is thin.
         */
        const val MINIMUM_PER_PERIOD = 20

        /** Long enough for any of these; short enough for the card's fixed band. */
        const val MAX_TEXT_LENGTH = 220
    }
}
