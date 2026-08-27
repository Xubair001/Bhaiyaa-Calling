package com.codeaza.bhaiyaaa.domain.model

/**
 * Three-state result for a single-row lookup.
 *
 * A plain nullable value cannot distinguish "the query hasn't answered yet"
 * from "there is genuinely no such row" - and a screen that renders those the
 * same way shows a "not found" error for a moment on every single open, which
 * reads as a bug to the user.
 */
sealed interface Lookup<out T> {
    data object Loading : Lookup<Nothing>
    data object Missing : Lookup<Nothing>
    data class Found<T>(val value: T) : Lookup<T>
}
