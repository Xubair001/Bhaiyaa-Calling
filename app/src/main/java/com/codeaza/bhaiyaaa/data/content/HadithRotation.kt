package com.codeaza.bhaiyaaa.data.content

import com.codeaza.bhaiyaaa.domain.model.Hadith
import kotlin.random.Random

/**
 * Chooses the next narration to show.
 *
 * Random selection alone repeats: with twenty-five entries and a card that
 * rotates every five minutes, the same one comes round twice in a row often
 * enough to look broken. So a short memory of what was just shown is excluded
 * from the draw, which is the whole of the "no immediate repetition" rule -
 * and deliberately not a lifetime history, which would eventually exhaust the
 * pool and need resetting anyway.
 *
 * Pure, with the source of randomness injected, so the behaviour is testable
 * rather than something to be observed by watching the screen.
 */
class HadithRotation(private val random: Random = Random.Default) {

    /**
     * @param recentIds most-recently-shown first. Longer than the pool is
     *   harmless - the memory is trimmed to what still leaves a choice.
     * @return null only when [pool] is empty.
     */
    fun next(pool: List<Hadith>, recentIds: List<String>): Hadith? {
        if (pool.isEmpty()) return null
        if (pool.size == 1) return pool.first()

        // Never remember so much that nothing is left to pick: with a pool of
        // three, remembering three would leave an empty draw every time.
        val memory = recentIds.take(memoryFor(pool.size)).toSet()
        val candidates = pool.filterNot { it.id in memory }
        // The filter can only empty the list if every id in the pool was
        // recently shown, which memoryFor already prevents - but falling back
        // to the full pool costs nothing and removes the crash entirely.
        val drawFrom = candidates.ifEmpty { pool }
        return drawFrom[random.nextInt(drawFrom.size)]
    }

    /** How many recent entries to exclude, given how many there are to choose from. */
    fun memoryFor(poolSize: Int): Int =
        (poolSize - 1).coerceIn(0, MAX_MEMORY)

    private companion object {
        /**
         * Enough that a repeat feels like coincidence rather than a bug, small
         * enough that a large pool still feels random rather than cyclic.
         */
        const val MAX_MEMORY = 8
    }
}
