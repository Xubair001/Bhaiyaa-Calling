package com.codeaza.bhaiyaaa.content

import com.codeaza.bhaiyaaa.data.content.HadithRotation
import com.codeaza.bhaiyaaa.domain.model.Hadith
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

/**
 * The "don't show me that again straight away" rule.
 *
 * Random selection alone repeats often enough to look broken, and a lifetime
 * history would eventually exhaust the pool. This is the middle: a short
 * memory that can never be long enough to empty the draw.
 */
class HadithRotationTest {

    private fun pool(size: Int) = (1..size).map {
        Hadith(
            id = "h$it",
            text = "Narration $it",
            narrator = null,
            reference = "Reference $it",
            grade = null,
            prayers = emptySet()
        )
    }

    @Test
    fun `an empty pool yields nothing rather than failing`() {
        assertThat(HadithRotation().next(emptyList(), emptyList())).isNull()
    }

    @Test
    fun `a pool of one always yields that one`() {
        val single = pool(1)
        // Even when it was just shown: there is nothing else to offer, and
        // returning null would blank the card.
        assertThat(HadithRotation().next(single, listOf("h1"))).isEqualTo(single.first())
    }

    @Test
    fun `what was just shown is not shown again`() {
        val rotation = HadithRotation(Random(1))
        val entries = pool(25)
        var recent = emptyList<String>()

        repeat(200) {
            val next = requireNotNull(rotation.next(entries, recent))
            assertThat(next.id).isNotIn(recent.take(rotation.memoryFor(entries.size)))
            recent = (listOf(next.id) + recent).take(rotation.memoryFor(entries.size))
        }
    }

    @Test
    fun `the memory is never long enough to empty the draw`() {
        // With three to choose from, remembering three would leave nothing.
        (1..40).forEach { size ->
            assertThat(HadithRotation().memoryFor(size)).isLessThan(size)
        }
    }

    @Test
    fun `a small pool still rotates rather than sticking`() {
        val rotation = HadithRotation(Random(7))
        val entries = pool(3)
        var recent = emptyList<String>()
        val seen = mutableSetOf<String>()

        repeat(30) {
            val next = requireNotNull(rotation.next(entries, recent))
            seen += next.id
            recent = (listOf(next.id) + recent).take(rotation.memoryFor(entries.size))
        }
        assertThat(seen).hasSize(3)
    }

    @Test
    fun `a recent list longer than the pool does not break the draw`() {
        val entries = pool(4)
        val everything = entries.map { it.id }
        // The caller trims, but the rotation must not depend on it having.
        assertThat(HadithRotation().next(entries, everything)).isNotNull()
    }
}
