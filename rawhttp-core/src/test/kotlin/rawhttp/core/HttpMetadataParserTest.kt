package rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.endWith
import io.kotlintest.matchers.include
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.properties.Table1
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import org.junit.Test
import rawhttp.core.errors.InvalidHttpHeader

class HttpMetadataParserTest {

    private val parser = HttpMetadataParser(RawHttpOptions.defaultInstance())
    private val errorCreator = { error: String, line: Int -> throw InvalidHttpHeader("$error($line)") }

    @Test
    fun canParseEmptyHeader() {
        val headers = parser.parseHeaders("".byteInputStream(), errorCreator)
        headers.asMap().keys should beEmpty()
    }

    @Test
    fun canParseSimpleHeaders() {
        val examples = table(
                headers("HTTP Header", "Name", "Value"),
                row("Accept: application/json", "Accept", "application/json"),
                row("Accept: text/xml;q=1.0,app/json;q=0.8,image/png+x", "Accept", "text/xml;q=1.0,app/json;q=0.8,image/png+x"),
                row("Content-Length:  42", "Content-Length", "42"),
                row("Content-Length:42   ", "Content-Length", "42")
        )

        forAll(examples) { header, expectedName, expectedValue ->
            val headers = parser.parseHeaders(header.byteInputStream(), errorCreator)
            headers.asMap().size shouldBe 1
            headers[expectedName] shouldEqual listOf(expectedValue)
        }
    }

    @Test
    fun canParseManyHeaders() {
        val allHeaders = """
            Date: Thu, 9 Aug 2018 17:42:09 GMT
            Server: RawHTTP
            Cache-Control: no-cache
            Pragma: no-cache
            Content-Type: text/plain
            Content-Length: 23
            X-Color: red
            X-Color: blue
        """.trimIndent()

        val headers = parser.parseHeaders(allHeaders.byteInputStream(), errorCreator)

        headers.asMap().keys shouldEqual setOf(
                "DATE", "SERVER", "CACHE-CONTROL", "PRAGMA",
                "CONTENT-TYPE", "CONTENT-LENGTH", "X-COLOR")

        headers["Date"] shouldEqual listOf("Thu, 9 Aug 2018 17:42:09 GMT")
        headers["Server"] shouldEqual listOf("RawHTTP")
        headers["Cache-Control"] shouldEqual listOf("no-cache")
        headers["Pragma"] shouldEqual listOf("no-cache")
        headers["Content-Type"] shouldEqual listOf("text/plain")
        headers["Content-Length"] shouldEqual listOf("23")
        headers["X-Color"] shouldEqual listOf("red", "blue")
    }

    @Test
    fun canParseWeirdHeaderNames() {
        val weirdNames = listOf("A!", "#H", "$$$", "4%0", "&X", "'A'", "*Y", "A+B", "A..", "A^", "X_Y", "`a", "X|Y", "~")

        forAll(weirdNames.asTable()) { weirdName ->
            val headers = parser.parseHeaders("$weirdName: value".byteInputStream(), errorCreator)
            headers.asMap().size shouldBe 1
            headers[weirdName] shouldEqual listOf("value")
        }
    }

    @Test
    fun cannotParseInvalidHeaderNames() {
        val badNames = listOf("A\"", "{A}", "?", "A()", "A/", ";B", "<A>", "C=A", "A@B", "[]", "A\\B", "ÅB", "Ä", "ão")

        forAll(badNames.asTable()) { badName ->
            val error = shouldThrow<InvalidHttpHeader> {
                val headers = parser.parseHeaders("$badName: value".byteInputStream(), errorCreator)
                print("Should have failed: $headers")
            }

            error.message!! should include("Illegal character in HTTP header name")
            error.message!! should endWith("(1)") // line number
        }
    }

    @Test
    fun canParseWeirdHeaderValues() {
        val weirdValues = listOf("A!", "#H", "$$$", "4%0", "&X", "'A'", "*Y", "A+B", "A..", "A^", "X_Y", "`a", "X|Y",
                "~", "(abc)", "[hi]", "a, b, c", "<html>", "A;{b}", "A???", "@2@", "1;a=b;c=d", "z\tx")

        forAll(weirdValues.asTable()) { weirdValue ->
            val headers = parser.parseHeaders("A: $weirdValue".byteInputStream(), errorCreator)
            headers.asMap().size shouldBe 1
            headers["A"] shouldEqual listOf(weirdValue)
        }
    }

    @Test
    fun cannotParseInvalidHeaderValues() {
        val badValues = listOf("ÅB", "Ä", "ão", "日本語")

        forAll(badValues.asTable()) { badValue ->
            val error = shouldThrow<InvalidHttpHeader> {
                val headers = parser.parseHeaders("A: $badValue".byteInputStream(), errorCreator)
                print("Should have failed: $headers")
            }

            error.message!! should include("Illegal character in HTTP header value")
            error.message!! should endWith("(1)") // line number
        }
    }

    private fun <E> List<E>.asTable(name: String = "value"): Table1<E> {
        val rows = map { row(it) }.toTypedArray()
        return table(headers(name), *rows)
    }

}
