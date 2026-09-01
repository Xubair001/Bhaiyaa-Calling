package com.codeaza.bhaiyaaa.domain.model

/**
 * How strongly a narration is authenticated.
 *
 * Carried on every entry rather than assumed, because it is the difference
 * between information and a claim. Bukhari and Muslim are not annotated in the
 * usual scholarly literature because their inclusion *is* the grading; anything
 * from the other collections states what the scholars graded it - al-Albani for
 * Abu Dawud, Darussalam for Tirmidhi and an-Nasa'i - so a reader can weigh it
 * rather than take the app's word for it.
 *
 * Where two collections disagree, the content file records the lower grade.
 * Nothing graded weak is included at all.
 */
enum class HadithGrade(val label: String) {
    SAHIH("Sahih"),
    /** Sound by one route, good by another - the grading the sources give. */
    HASAN_SAHIH("Hasan sahih"),
    HASAN("Hasan");

    companion object {
        fun from(value: String?): HadithGrade? =
            entries.firstOrNull { it.name.equals(value?.replace(' ', '_'), ignoreCase = true) }
    }
}

/**
 * One narration, with everything needed to check it.
 *
 * The text is an English rendering of the meaning, never a claim to be the
 * Arabic wording, and [reference] names the collection so any entry can be
 * looked up independently of this app. Nothing here is generated, paraphrased
 * for effect, or attributed loosely - the content lives in an asset file that
 * can be reviewed on its own, and this type is only the shape it is read into.
 */
data class Hadith(
    /** Stable across content edits, so "don't repeat" survives a reordering. */
    val id: String,
    val text: String,
    /** Who narrated it. Null when the source names no companion. */
    val narrator: String?,
    /** Collection and number, e.g. "Sahih al-Bukhari 574". */
    val reference: String,
    val grade: HadithGrade?,
    /**
     * The prayer periods this belongs to.
     *
     * Empty means it is about prayer generally and fits any period. That is
     * the honest way to reach a useful number per period without inventing
     * a link between a narration and a prayer it never mentioned - which is
     * what padding each period to a target separately would have required.
     */
    val prayers: Set<Prayer>
) {
    fun appliesTo(prayer: Prayer): Boolean = prayers.isEmpty() || prayer in prayers

    /** True when this narration names the prayer specifically. */
    fun isSpecificTo(prayer: Prayer): Boolean = prayer in prayers
}
