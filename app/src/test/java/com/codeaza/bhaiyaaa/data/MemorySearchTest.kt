package com.codeaza.bhaiyaaa.data

import com.codeaza.bhaiyaaa.data.repository.MemorySearch
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * User text reaches SQLite's FTS MATCH parser, where stray punctuation is
 * syntax. These tests pin the sanitising so a search can't throw or be
 * reinterpreted as an operator.
 */
class MemorySearchTest {

    @Test
    fun `ordinary words become quoted terms with a prefix match on the last`() {
        val query = MemorySearch.toFtsQuery("deployment friday")
        assertThat(query).isEqualTo("\"deployment\" OR \"friday\"*")
    }

    @Test
    fun `question words are dropped as noise`() {
        val query = requireNotNull(MemorySearch.toFtsQuery("what did Ahmed say about the deployment"))
        assertThat(query).contains("ahmed")
        assertThat(query).contains("deployment")
        assertThat(query).doesNotContain("\"what\"")
        assertThat(query).doesNotContain("\"about\"")
    }

    @Test
    fun `punctuation cannot leak into the fts expression`() {
        // A raw quote or asterisk here would be an FTS syntax error.
        val query = MemorySearch.toFtsQuery("invoice \"urgent\" * OR NOT")
        assertThat(query).isNotNull()
        assertThat(query!!.count { it == '"' } % 2).isEqualTo(0)
        assertThat(query).contains("invoice")
    }

    @Test
    fun `a query with nothing searchable returns null`() {
        assertThat(MemorySearch.toFtsQuery("?? !! ...")).isNull()
        assertThat(MemorySearch.toFtsQuery("the a an")).isNull()
        assertThat(MemorySearch.toFtsQuery("")).isNull()
    }

    @Test
    fun `tokenizer keeps unicode words and drops single characters`() {
        val tokens = MemorySearch.tokenize("Ahmed's café a b deployment")
        assertThat(tokens).contains("ahmed")
        assertThat(tokens).contains("café")
        assertThat(tokens).contains("deployment")
        assertThat(tokens).doesNotContain("a")
        assertThat(tokens).doesNotContain("b")
    }

    @Test
    fun `ranking prefers results matching more query terms`() {
        val items = listOf("only deployment", "deployment on friday", "unrelated")
        val ranked = MemorySearch.rank(items, "deployment friday") { it }
        assertThat(ranked.first()).isEqualTo("deployment on friday")
    }
}
