package rawhttp.core

import io.kotlintest.matchers.shouldBe
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec

class FieldValuesTests : StringSpec({

    /*
    token          = 1*tchar

    tchar          = "!" / "#" / "$" / "%" / "&" / "'" / "*"
                     / "+" / "-" / "." / "^" / "_" / "`" / "|" / "~"
                     / DIGIT / ALPHA
    ; any VCHAR, except delimiters
     */
    "Valid token values" {
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
            FieldValues.indexOfNotAllowedInTokens(text).orElse(-1) shouldBe -1
        }
    }

    "Invalid tokens" {
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
            FieldValues.indexOfNotAllowedInTokens(text).orElse(-1) shouldBe index
        }
    }

    "Valid VCHARS" {
        val myTable = table(
                headers("Valid VCHAR"),
                row("hello"),
                row("Accept"),
                row("*/*"),
                row("application/json"),
                row("application/json+scim"),
                row("read write"),
                row("q=0.1,x=b,a:c"),
                row("Hötorget"),
                row("Åsa"),
                row("pão"),
                row("água"),
                row("Paralelepípedo"),
                row("Piauí"),
                // just use every acceptable character in a single String
                row(String((' '..'~').toList().toCharArray()))
        )
        forAll(myTable) { text ->
            FieldValues.indexOfNotAllowedInHeaderValue(text).orElse(-1) shouldBe -1
        }
    }

    "Invalid Header Values" {
        val myTable = table(
                headers("Invalid VCHAR", "index"),
                row(("\u001f").toString(), 0),
                row(("abc\u001fdef").toString(), 3),
                row(("1234567\u001f").toString(), 7),
                row(("\u007fboo").toString(), 0),
                row(("boo\u007fbar").toString(), 3)
        )
        forAll(myTable) { text, index ->
            FieldValues.indexOfNotAllowedInHeaderValue(text).orElse(-1) shouldBe index
        }
    }

})