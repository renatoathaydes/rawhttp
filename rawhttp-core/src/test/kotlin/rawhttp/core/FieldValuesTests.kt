package rawhttp.core

import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FieldValuesTests {

    val asciiTable = (0..127).toList()

    @Test
    fun `Valid token values`() {
        val myTable = table(
                headers("Valid Token"),
                row("hello"),
                row("Accept"),
                row("Hello-World"),
                row("Hello_World"),
                row("123"),
                row("Hi123"),
                row("Acc!ept"),
                row("|Acc#ept|"),
                row("Accept~"),
                row("Accep\$t"),
                row("Accept&Do"),
                row("**%Accept**"),
                row("'Accept'"),
                row("Accept+Do"),
                row("^Accept.Do"),
                row("^Accept`Do`")
        )
        forAll(myTable) { text ->
            assertEquals(-1, FieldValues.indexOfNotAllowedInTokens(text).orElse(-1), "Example: $text")
        }
    }

    @Test
    fun `Invalid tokens`() {
        val myTable = table(
                headers("Invalid Token", "index"),
                row(" ", 0),
                row("A B", 1),
                row("A@B", 1),
                row("Accept{Maybe}", 6),
                row("(BOO)", 0),
                row("Hello<You>", 5),
                row("One=1", 3),
                row("Two2]", 4),
                row("Hötorget", 1),
                row("Åsa", 0),
                row("pão", 1),
                row("água", 0),
                row("Paralelepípedo", 9),
                row("Piauí", 4)
        )
        forAll(myTable) { text, index ->
            assertEquals(index, FieldValues.indexOfNotAllowedInTokens(text).orElse(-1), "Example: $text")
        }
    }

    @Test
    fun `Comprehensively check every ASCII char and byte against validity in tokens`() {
        /*
            token          = 1*tchar

            tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
                             / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
                             / DIGIT / ALPHA
            ; any VCHAR, except delimiters

            delimiters: (),/:;<=>?@[\]{}
         */
        for (b in asciiTable) {
            val c = b.toChar()
            val expectedInTokens = when {
                c in 'a'..'z' -> true
                c in 'A'..'Z' -> true
                c in '0'..'9' -> true
                else -> when (c) {
                    '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~' -> true
                    else -> false
                }
            }
            assertEquals(expectedInTokens, FieldValues.isAllowedInTokens(b), "Example: $b")
            assertEquals(expectedInTokens, FieldValues.isAllowedInTokens(c), "Example: '$c'")
        }
    }

    @Test
    fun `Comprehensively check every non-ASCII byte against validity in tokens`() {
        for (b in 128..256) {
            val c = b.toChar()
            assertEquals(false, FieldValues.isAllowedInTokens(b), "Example: $b")
            assertEquals(false, FieldValues.isAllowedInTokens(c), "Example: '$c'")
        }
    }

    @Test
    fun `Comprehensively check every byte against validity in VCHARS`() {
        for (b in 0..256) {
            val c = b.toChar()
            val expectedInVchars = when {
                c in 'a'..'z' -> true
                c in 'A'..'Z' -> true
                c in '0'..'9' -> true
                else -> when (c) {
                    '!', '#', '$', '%', '&', '\'', '*', '+', '-', '.', '^', '_', '`', '|', '~',
                    '"', '(', ')', ',', '/', ':', ';', '<', '=', '>', '?', '@', '[', '\\', ']', '{', '}' -> true
                    else -> false
                }
            }
            assertEquals(expectedInVchars, FieldValues.isAllowedInVCHARs(c), "char '$c'")
            assertEquals(expectedInVchars, FieldValues.isAllowedInVCHARs(b), "byte $b")
        }
    }

    @Test
    fun `Typical valid Header Values`() {
        val values = listOf("*/*", "text/html", "application/json; charset=utf-8", "Mon, 27 Jul 2009 12:28:53 GMT")
        for (value in values) {
            assertEquals(-1, FieldValues.indexOfNotAllowedInHeaderValue(value).orElse(-1), "Example: $value")
        }
    }

    @Test
    fun `Unusual but valid Header Values`() {
        val values = listOf("Schrödinger", "pão-de-ló")
        for (value in values) {
            assertEquals(-1, FieldValues.indexOfNotAllowedInHeaderValue(value).orElse(-1), "Example: $value")
        }
    }

    @Test
    fun `Invalid Header Values`() {
        val myTable = table(
                headers("Invalid Header Value", "index"),
                row(("\u001f").toString(), 0),
                row(("abc\u001fdef").toString(), 3),
                row(("1234567\u001f").toString(), 7),
                row(("\u007fboo").toString(), 0),
                row(("boo\u007fbar").toString(), 3),
                // non-ISO characters not allowed by default as it's not recommended
                row("こんにちは", 0)
        )
        forAll(myTable) { text, index ->
            assertEquals(index, FieldValues.indexOfNotAllowedInHeaderValue(text).orElse(-1), "Example: $text")
        }
    }

}