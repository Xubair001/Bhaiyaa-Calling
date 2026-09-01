package com.codeaza.bhaiyaaa.data.content

import android.content.Context
import android.util.Log
import com.codeaza.bhaiyaaa.domain.model.Hadith
import com.codeaza.bhaiyaaa.domain.model.HadithGrade
import com.codeaza.bhaiyaaa.domain.model.Prayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * The Hadith content layer.
 *
 * Content lives in `assets/hadith/hadith.json` and nowhere else. Keeping it out
 * of Kotlin is the point: religious text has to be reviewable and correctable
 * by someone who is not going to open Android Studio, and a reference that
 * turns out to be wrong should be a one-line edit to a data file rather than a
 * code change. It is also why this is not a Room table - the content is the
 * same for every user, never edited on the device, and putting it in the
 * database would buy nothing but a migration every time a narration is added.
 *
 * Parsed once per process and held in memory. The whole file is a few tens of
 * kilobytes, so re-reading it for every card would be pure waste, and the parse
 * is done on the IO dispatcher so the first read cannot touch a frame.
 */
class HadithRepository(context: Context) {

    private val appContext = context.applicationContext

    /**
     * The prayer-period pools, ready to draw from.
     *
     * @return every narration that fits [prayer] - the ones that name it, plus
     *   the ones about prayer generally. Ordered specific-first so a caller
     *   that wants only the pointed ones can take from the front.
     */
    suspend fun forPeriod(prayer: Prayer): List<Hadith> =
        all().filter { it.appliesTo(prayer) }
            .sortedByDescending { it.isSpecificTo(prayer) }

    /** Every entry in the content file, in file order. */
    suspend fun all(): List<Hadith> {
        cached?.let { return it }
        return loadLock.withLock {
            cached ?: withContext(Dispatchers.IO) { parse() }.also { cached = it }
        }
    }

    private fun parse(): List<Hadith> = try {
        val json = appContext.assets.open(ASSET_PATH)
            .bufferedReader()
            .use { it.readText() }
        val entries = JSONObject(json).getJSONArray("hadith")
        (0 until entries.length()).mapNotNull { index ->
            runCatching { entries.getJSONObject(index).toHadith() }.getOrNull()
        }
    } catch (e: Exception) {
        // A missing or malformed content file must not take a screen down. The
        // card simply does not appear, which is the correct failure for
        // something informational.
        Log.w(TAG, "Hadith content unavailable: ${e.javaClass.simpleName}")
        emptyList()
    }

    private fun JSONObject.toHadith(): Hadith {
        val prayers = optJSONArray("prayers")
        return Hadith(
            id = getString("id"),
            text = getString("text"),
            narrator = optString("narrator").takeIf { it.isNotBlank() },
            reference = getString("reference"),
            grade = HadithGrade.from(optString("grade").takeIf { it.isNotBlank() }),
            prayers = buildSet {
                for (i in 0 until (prayers?.length() ?: 0)) {
                    val value = prayers?.optString(i).orEmpty()
                    // Only add a prayer the enum actually knows: an unknown
                    // string must not silently become Fajr, which is what
                    // Prayer.from would do.
                    Prayer.entries.firstOrNull { it.storageValue == value }?.let(::add)
                }
            }
        )
    }

    private companion object {
        const val ASSET_PATH = "hadith/hadith.json"
        const val TAG = "SukoonHadith"

        /**
         * Process-wide: the content is immutable and identical for everyone,
         * so parsing it per screen or per view model would be repeated work
         * for an identical answer.
         */
        @Volatile
        var cached: List<Hadith>? = null
        val loadLock = Mutex()
    }
}
