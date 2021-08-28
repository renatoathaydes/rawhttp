package rawhttp.core

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.Table1
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldInclude
import org.junit.jupiter.api.Test
import rawhttp.core.errors.InvalidHttpHeader

data class UriExample(
    val uriSpec: String,
    val scheme: String?,
    val userInfo: String?,
    val host: String?,
    val port: Int,
    val path: String?,
    val query: String?,
    val fragment: String?
)

fun uriExample(uriSpec: String,
               scheme: String?,
               userInfo: String?,
               host: String?,
               port: Int,
               path: String?,
               query: String?,
               fragment: String?) = row(UriExample(uriSpec, scheme, userInfo, host, port, path, query, fragment))

class HttpMetadataParserTest {

    private val parser = HttpMetadataParser(RawHttpOptions.defaultInstance())
    private val errorCreator = { error: String, line: Int -> throw InvalidHttpHeader("$error($line)") }

    @Test
    fun canParseEmptyHeader() {
        val headers = parser.parseHeaders("".byteInputStream(), errorCreator)
        headers.asMap().keys shouldHaveSize 0
    }

    @Test
    fun canParseHeaderWithEmptyValue() {
        val headers = parser.parseHeaders("A:".byteInputStream(), errorCreator)
        headers.asMap().keys shouldBe setOf("A")
        headers.toString() shouldBe "A: \r\n"
    }

    @Test
    fun shouldTrimHeaderValue() {
        val headers = parser.parseHeaders("A:  abc   \r\n".byteInputStream(), errorCreator)
        headers.asMap().keys shouldBe setOf("A")
        headers["A"] shouldBe listOf("abc")
        headers.toString() shouldBe "A: abc\r\n"
    }

    @Test
    fun canParseSimpleHeaders() {
        val examples = table(
            headers("HTTP Header", "Name", "Value"),
            row("Accept: application/json", "Accept", "application/json"),
            row(
                "Accept: text/xml;q=1.0,app/json;q=0.8,image/png+x",
                "Accept",
                "text/xml;q=1.0,app/json;q=0.8,image/png+x"
            ),
            row("Content-Length:  42", "Content-Length", "42"),
            row("Content-Length:42   ", "Content-Length", "42")
        )

        forAll(examples) { header, expectedName, expectedValue ->
            val headers = parser.parseHeaders(header.byteInputStream(), errorCreator)
            headers.asMap().size shouldBe 1
            headers[expectedName] shouldBe listOf(expectedValue)
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

        headers.asMap().keys shouldBe setOf(
            "DATE", "SERVER", "CACHE-CONTROL", "PRAGMA",
            "CONTENT-TYPE", "CONTENT-LENGTH", "X-COLOR"
        )

        headers["Date"] shouldBe listOf("Thu, 9 Aug 2018 17:42:09 GMT")
        headers["Server"] shouldBe listOf("RawHTTP")
        headers["Cache-Control"] shouldBe listOf("no-cache")
        headers["Pragma"] shouldBe listOf("no-cache")
        headers["Content-Type"] shouldBe listOf("text/plain")
        headers["Content-Length"] shouldBe listOf("23")
        headers["X-Color"] shouldBe listOf("red", "blue")
    }

    @Test
    fun canParseManyHeadersWithComments() {
        val allHeaders = """
            # this is a date
            Date: Thu, 9 Aug 2018 17:42:09 GMT
            Server: RawHTTP
            # caching strategy
            Cache-Control: no-cache
            Pragma: no-cache
            # this is a content-type!!!
            Content-Type: text/plain
            Content-Length: 23
            # my custom headers
            X-Color: red
            X-Color: blue
        """.trimIndent()

        val parserWithComments = HttpMetadataParser(
            RawHttpOptions.newBuilder()
                .allowComments()
                .build()
        )

        val headers = parserWithComments.parseHeaders(allHeaders.byteInputStream(), errorCreator)

        headers.asMap().keys shouldBe setOf(
            "DATE", "SERVER", "CACHE-CONTROL", "PRAGMA",
            "CONTENT-TYPE", "CONTENT-LENGTH", "X-COLOR"
        )

        headers["Date"] shouldBe listOf("Thu, 9 Aug 2018 17:42:09 GMT")
        headers["Server"] shouldBe listOf("RawHTTP")
        headers["Cache-Control"] shouldBe listOf("no-cache")
        headers["Pragma"] shouldBe listOf("no-cache")
        headers["Content-Type"] shouldBe listOf("text/plain")
        headers["Content-Length"] shouldBe listOf("23")
        headers["X-Color"] shouldBe listOf("red", "blue")
    }

    @Test
    fun canParseWeirdHeaderNames() {
        val weirdNames =
            listOf("A!", "#H", "$$$", "4%0", "&X", "'A'", "*Y", "A+B", "A..", "A^", "X_Y", "`a", "X|Y", "~")

        forAll(weirdNames.asTable()) { weirdName ->
            val headers = parser.parseHeaders("$weirdName: value".byteInputStream(), errorCreator)
            headers.asMap().size shouldBe 1
            headers[weirdName] shouldBe listOf("value")
        }
    }

    @Test
    fun cannotParseInvalidHeaderNames() {
        val badNames = listOf("A\"", "{A}", "?", "A()", "A/", ";B", "<A>", "C=A", "A@B", "[]", "A\\B", "ÅB", "Ä", "ão")

        forAll(badNames.asTable()) { badName ->
            val error = shouldThrow<InvalidHttpHeader> {
                val headers =
                    parser.parseHeaders("$badName: value".byteInputStream(charset = Charsets.ISO_8859_1), errorCreator)
                print("Should have failed: $headers")
            }

            error.message!! shouldInclude ("Illegal character in HTTP header name")
            error.message!! shouldEndWith ("(1)") // line number
        }
    }

    @Test
    fun canParseWeirdHeaderValues() {
        val weirdValues = listOf(
            "A!", "#H", "$$$", "4%0", "&X", "'A'", "*Y", "A+B", "A..", "A^", "X_Y", "`a", "X|Y",
            "~", "(abc)", "[hi]", "a, b, c", "<html>", "A;{b}", "A???", "@2@", "1;a=b;c=d", "z\tx",
            "\u00DEbc", "ab\u00FFc", "p\u00E3o"
        )

        forAll(weirdValues.asTable()) { weirdValue ->
            val headers =
                parser.parseHeaders("A: $weirdValue".byteInputStream(charset = Charsets.ISO_8859_1), errorCreator)
            headers.asMap().size shouldBe 1
            headers["A"] shouldBe listOf(weirdValue)
        }
    }

    @Test
    fun shouldUseProvidedHeaderValueCharset() {
        val utf8Valuesparser = HttpMetadataParser(
            RawHttpOptions.newBuilder()
                .withHttpHeadersOptions()
                .withValuesCharset(Charsets.UTF_8).done().build()
        )

        val headers = utf8Valuesparser.parseHeaders(
            ("Hello: こんにちは\r\n" +
                    "Bye: さようなら\r\n").byteInputStream(Charsets.UTF_8), errorCreator
        )
        headers["Hello"] shouldBe listOf("こんにちは")
        headers["Bye"] shouldBe listOf("さようなら")
    }

    @Test
    fun `should use ISO-8859-1 header value charset by default`() {
        val headers = parser.parseHeaders(
            ("Hello: Hallå\r\n" +
                    "Bye: hej då\r\n").byteInputStream(Charsets.ISO_8859_1), errorCreator
        )
        headers["Hello"] shouldBe listOf("Hallå")
        headers["Bye"] shouldBe listOf("hej då")
    }

    @Test
    fun cannotParseInvalidHeaderValues() {
        val badValues = listOf("hi\u007F", "ab\u0000c")

        forAll(badValues.asTable()) { badValue ->
            val error = shouldThrow<InvalidHttpHeader> {
                val headers =
                    parser.parseHeaders("A: $badValue".byteInputStream(charset = Charsets.ISO_8859_1), errorCreator)
                print("Should have failed: $headers")
            }

            error.message!! shouldInclude ("Illegal character in HTTP header value")
            error.message!! shouldEndWith ("(1)") // line number
        }
    }

    @Test
    fun headerNameMustNotContainWhitespace() {
        val allHeaders = """
            Date: Thu, 9 Aug 2018 17:42:09 GMT
            Server : RawHTTP
            Cache-Control: no-cache
        """.trimIndent()

        val error = shouldThrow<InvalidHttpHeader> {
            val headers = parser.parseHeaders(allHeaders.byteInputStream(), errorCreator)
            print("Should have failed: $headers")
        }

        error.message!! shouldInclude ("Illegal character in HTTP header name")
        error.message!! shouldEndWith ("(2)") // line number
    }

    @Test
    fun canLimitHeaderNameLength() {
        val lowMaxHeaderNameLimitParser = HttpMetadataParser(
            RawHttpOptions.newBuilder()
                .withHttpHeadersOptions()
                .withMaxHeaderNameLength(6)
                .done().build()
        )

        val examples = table(
            headers("Header Name", "Should pass"),
            row("X", true),
            row("Conten", true),
            row("Content", false),
            row("Content-Type", false),
            row("AVERYLARGEHEADERNAMEWHICHMUSTNOTBEACCEPTED", false)
        )

        forAll(examples) { headerName, shouldPass ->
            val parse = {
                lowMaxHeaderNameLimitParser.parseHeaders(
                    "$headerName: OK".byteInputStream(), errorCreator
                )
            }
            if (shouldPass) {
                val headers = parse()
                headers.asMap().size shouldBe 1
                headers[headerName] shouldBe listOf("OK")
            } else {
                val error = shouldThrow<InvalidHttpHeader> {
                    val headers = parse()
                    print("Should have failed: $headers")
                }
                error.message!! shouldBe "Header name is too long(1)"
            }
        }
    }

    @Test
    fun canLimitHeaderValueLength() {
        val lowMaxHeaderNameLimitParser = HttpMetadataParser(
            RawHttpOptions.newBuilder()
                .withHttpHeadersOptions()
                .withMaxHeaderValueLength(6)
                .done().build()
        )

        val examples = table(
            headers("Header Value", "Should pass"),
            row("X", true),
            row("Conten", true),
            row("Content", false),
            row("Content-Type", false),
            row("AVERYLARGEHEADERNAMEWHICHMUSTNOTBEACCEPTED", false)
        )

        forAll(examples) { headerValue, shouldPass ->
            val parse = {
                lowMaxHeaderNameLimitParser.parseHeaders(
                    "Header: $headerValue".byteInputStream(), errorCreator
                )
            }
            if (shouldPass) {
                val headers = try {
                    parse()
                } catch (e: InvalidHttpHeader) {
                    fail("Example: '$headerValue' failed due to $e")
                }
                headers.asMap().size shouldBe 1
                headers["Header"] shouldBe listOf(headerValue)
            } else {
                val error = shouldThrow<InvalidHttpHeader> {
                    val headers = parse()
                    print("Should have failed: $headers")
                }
                error.message!! shouldBe "Header value is too long(1)"
            }
        }
    }

    @Test
    fun canValidateHeaders() {
        val nonDuplicatesAllowedParser = HttpMetadataParser(RawHttpOptions.newBuilder()
            .withHttpHeadersOptions()
            .withValidator { headers ->
                val names = mutableSetOf<String>()
                headers.forEach { name, _ ->
                    val isDuplicate = !names.add(name)
                    if (isDuplicate) {
                        throw InvalidHttpHeader("Duplicate header: $name")
                    }
                }
            }
            .done()
            .build())

        // duplicate headers should FAIL
        val duplicateHeaders = """
            Date: Thu, 9 Aug 2018 17:42:09 GMT
            Server: RawHTTP
            X-Color: red
            X-Color: blue
        """.trimIndent()

        val error = shouldThrow<InvalidHttpHeader> {
            val headers = nonDuplicatesAllowedParser.parseHeaders(duplicateHeaders.byteInputStream(), errorCreator)
            print("Should have failed: $headers")
        }

        error.message shouldBe "Duplicate header: X-Color"

        // non-duplicate headers should PASS
        val allHeaders = """
            Date: Thu, 9 Aug 2018 17:42:09 GMT
            Server: RawHTTP
            X-Color: red
            Y-Color: blue
        """.trimIndent()

        val headers = nonDuplicatesAllowedParser.parseHeaders(allHeaders.byteInputStream(), errorCreator)

        // make sure the parsing worked
        headers.asMap().keys shouldBe setOf("DATE", "SERVER", "X-COLOR", "Y-COLOR")
    }

    @Test
    fun canParseURIsNonLenient() {
        val table = table(
            headers("URI spec"),
            uriExample("hi", "http", null, "hi", -1, "", null, null),
            uriExample(
                "/hello?encoded=%2F%2Fencoded%3Fa%3Db%26c%3Dd&json=%7B%22a%22%3A%20null%7D", "http",
                null, null, -1, "/hello",
                "encoded=%2F%2Fencoded%3Fa%3Db%26c%3Dd&json=%7B%22a%22%3A%20null%7D", null
            ))

        val parser = HttpMetadataParser(RawHttpOptions.defaultInstance())

        forAll(table) { (uriSpec, scheme, userInfo, host, port, path, query, fragment) ->
            val uri = parser.parseUri(uriSpec)
            uri.scheme shouldBe scheme
            uri.userInfo shouldBe userInfo
            uri.host shouldBe host
            uri.port shouldBe port
            uri.path shouldBe path
            uri.rawQuery shouldBe query
            uri.rawFragment shouldBe fragment
        }
    }

    @Test
    fun canParseURIsLenient() {
        val table = table(
            headers("URI spec"),
            uriExample("hi", "http", null, "hi", -1, "", null, null),
            uriExample("hi.com", "http", null, "hi.com", -1, "", null, null),
            uriExample("hi.com:80", "http", null, "hi.com", 80, "", null, null),
            uriExample("https://hi.com:80/", "https", null, "hi.com", 80, "/", null, null),
            uriExample("hi.com/hello", "http", null, "hi.com", -1, "/hello", null, null),
            uriExample("hi?", "http", null, "hi", -1, "", "", null),
            uriExample("hi?abc", "http", null, "hi", -1, "", "abc", null),
            uriExample("hi?a b", "http", null, "hi", -1, "", "a%20b", null),
            uriExample("hi#", "http", null, "hi", -1, "", null, ""),
            uriExample("hi#def", "http", null, "hi", -1, "", null, "def"),
            uriExample("hi#d^f", "http", null, "hi", -1, "", null, "d%5Ef"),
            uriExample("hi#a?b", "http", null, "hi", -1, "", null, "a?b"),
            uriExample("hi?a#d", "http", null, "hi", -1, "", "a", "d"),
            uriExample("hi?a!#d@f", "http", null, "hi", -1, "", "a!", "d@f"),
            uriExample("x://admin@hello", "x", "admin", "hello", -1, "", null, null),
            uriExample(
                "x://admin:pass@hello.com/hi?boo&bar#abc",
                "x",
                "admin:pass",
                "hello.com",
                -1,
                "/hi",
                "boo&bar",
                "abc"
            ),
            uriExample(
                "https://admin:pass@hello:8443/hi?boo&bar#abc",
                "https",
                "admin:pass",
                "hello",
                8443,
                "/hi",
                "boo&bar",
                "abc"
            ),
            uriExample("http://0.0.0.0:8080", "http", null, "0.0.0.0", 8080, "", null, null),
            uriExample("0.0.0.0:8080", "http", null, "0.0.0.0", 8080, "", null, null),
            uriExample("renato:pass@192.168.0.1:83", "http", "renato:pass", "192.168.0.1", 83, "", null, null),
            uriExample(
                "[2001:db8:85a3:0:0:8a2e:370:7334]",
                "http",
                null,
                "[2001:db8:85a3:0:0:8a2e:370:7334]",
                -1,
                "",
                null,
                null
            ),
            uriExample("[::8a2e:370:7334]:43", "http", null, "[::8a2e:370:7334]", 43, "", null, null),
            uriExample(
                "ftp://user:pass@[::8a2e:370:7334]/hi?ho",
                "ftp",
                "user:pass",
                "[::8a2e:370:7334]",
                -1,
                "/hi",
                "ho",
                null
            ),
            uriExample("ftp://[::8a2e:370:7334]#ho", "ftp", null, "[::8a2e:370:7334]", -1, "", null, "ho")
        )

        val lenientParser = HttpMetadataParser(
            RawHttpOptions.newBuilder()
                .allowIllegalStartLineCharacters()
                .build()
        )

        forAll(table) { (uriSpec, scheme, userInfo, host, port, path, query, fragment) ->
            val uri = lenientParser.parseUri(uriSpec)
            uri.scheme shouldBe scheme
            uri.userInfo shouldBe userInfo
            uri.host shouldBe host
            uri.port shouldBe port
            uri.path shouldBe path
            uri.rawQuery shouldBe query
            uri.rawFragment shouldBe fragment
        }
    }

    @Test
    fun canParseQueryStrings() {
        val table = table(
            headers("Query String", "Expected query parameters"),
            row("", mapOf()),
            row("&", mapOf()),
            row("=", mapOf("" to listOf(""))),
            row("hello", mapOf("hello" to listOf<String>())),
            row("hello=", mapOf("hello" to listOf(""))),
            row("hello=true", mapOf("hello" to listOf("true"))),
            row("a=1&b=2", mapOf("a" to listOf("1"), "b" to listOf("2"))),
            row("a=1&a=2&b=3&a=4", mapOf("a" to listOf("1", "2", "4"), "b" to listOf("3"))),
            row("hello=hi%20there", mapOf("hello" to listOf("hi there"))),
            row(
                "hello%20there=hi&foo=a%26b", mapOf(
                    "hello there" to listOf("hi"),
                    "foo" to listOf("a&b")
                )
            )
        )

        forAll(table) { queryString, expectedParameters ->
            val parameters = parser.parseQueryString(queryString)
            parameters shouldBe expectedParameters
        }
    }

    private fun <E> List<E>.asTable(name: String = "value"): Table1<E> {
        val rows = map { row(it) }.toTypedArray()
        return table(headers(name), *rows)
    }

}
