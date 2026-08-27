package com.codeaza.bhaiyaaa.data.repository

import java.util.Locale

/**
 * Turns a human phrase into a safe FTS4 MATCH expression.
 *
 * FTS treats a lot of punctuation as query syntax, so raw user text can throw a
 * SQLite error or - worse - silently mean something else. Every token is
 * therefore stripped to word characters and quoted, and a trailing `*` makes
 * the last token a prefix match so results appear while the user is still
 * typing.
 */
object MemorySearch {

    /** Words that carry no signal in a search over one person's own notes. */
    private val STOP_WORDS = setOf(
        "the", "a", "an", "about", "did", "do", "does", "say", "said", "tell", "told",
        "me", "my", "i", "what", "when", "was", "is", "are", "to", "of", "on", "for",
        "with", "and", "or", "that", "this", "it", "you"
    )

    /** Returns null when the phrase has nothing searchable left in it. */
    fun toFtsQuery(raw: String): String? {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return null
        // Quote each token so punctuation can never be read as FTS operators,
        // and prefix-match the final one for type-ahead behaviour.
        val quoted = tokens.mapIndexed { index, token ->
            if (index == tokens.lastIndex) "\"$token\"*" else "\"$token\""
        }
        return quoted.joinToString(" OR ")
    }

    /** Tokens for the LIKE fallback and for relevance ranking. */
    fun tokenize(raw: String): List<String> = raw
        .lowercase(Locale.ROOT)
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 1 && it !in STOP_WORDS }
        .distinct()

    /**
     * Ranks FTS hits by how many query tokens they actually contain. FTS4 has no
     * built-in relevance without the rank extension, and "most terms matched" is
     * a good enough ordering for a personal notebook.
     */
    fun <T> rank(items: List<T>, raw: String, textOf: (T) -> String): List<T> {
        val tokens = tokenize(raw)
        if (tokens.isEmpty()) return items
        return items.sortedByDescending { item ->
            val text = textOf(item).lowercase(Locale.ROOT)
            tokens.count { text.contains(it) }
        }
    }
}
