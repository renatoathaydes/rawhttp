package rawhttp.core

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.errors.InvalidHttpRequest
import java.net.URI

class RequestLineTest {

    val metadataParser = HttpMetadataParser(RawHttpOptions.defaultInstance())

    val strictMetadataParser = HttpMetadataParser(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .doNotIgnoreLeadingEmptyLine()
            .doNotInsertHttpVersionIfMissing()
            .build())

    @Test
    fun canParseLegalRequestLine__allowMissingHTTPVersion() {
        val table = table(
                headers("Request line", "Expected version", "Expected method", "Expected path", "Expected String"),
                row("GET /", HttpVersion.HTTP_1_1, "GET", "/", "GET / HTTP/1.1"),
                row("POST /hello", HttpVersion.HTTP_1_1, "POST", "/hello", "POST /hello HTTP/1.1"),
                row("POST /hello HTTP/1.1", HttpVersion.HTTP_1_1, "POST", "/hello", "POST /hello HTTP/1.1"),
                row("do /hello HTTP/1.0", HttpVersion.HTTP_1_0, "do", "/hello", "do /hello HTTP/1.0"),
                row("GET /hello?a=1&b=2 HTTP/1.0", HttpVersion.HTTP_1_0, "GET", "/hello", "GET /hello?a=1&b=2 HTTP/1.0"))

        forAll(table) { requestLine, expectedVersion, expectedMethod, expectedPath, expectedString ->
            try {
                metadataParser.parseRequestLine(requestLine).run {
                    httpVersion shouldBe expectedVersion
                    method shouldBe expectedMethod
                    uri.path shouldBe expectedPath
                    toString() shouldBe expectedString
                }
            } catch (e: InvalidHttpRequest) {
                fail(e.toString())
            }
        }
    }

    @Test
    fun canParseLegalRequestLine__allowIllegalCharacters() {
        val illegalCharParser = HttpMetadataParser(RawHttpOptions.newBuilder()
                .allowIllegalStartLineCharacters()
                .build())

        val table = table(
                headers("Request line", "Expected version", "Expected method", "Expected path", "Expected String"),
                row("GET /hi there HTTP/1.1", HttpVersion.HTTP_1_1, "GET", "/hi%20there", "GET /hi%20there HTTP/1.1"),
                row("POST /api/users/test@example.com HTTP/1.1", HttpVersion.HTTP_1_1,
                        "POST", "/api/users/test@example.com", "POST /api/users/test@example.com HTTP/1.1")
        )

        forAll(table) { requestLine, expectedVersion, expectedMethod, expectedPath, expectedString ->
            try {
                illegalCharParser.parseRequestLine(requestLine).run {
                    httpVersion shouldBe expectedVersion
                    method shouldBe expectedMethod
                    uri.rawPath shouldBe expectedPath
                    toString() shouldBe expectedString
                }
            } catch (e: InvalidHttpRequest) {
                fail(e.toString())
            }
        }
    }

    @Test
    fun cannotParseIllegalRequestLine__allowMissingHTTPVersion() {
        val table = table(headers("Request line", "Expected error"),
                row("", "No content"),
                row("/", "Invalid request line: '/'"),
                row("GET", "Invalid request line: 'GET'"),
                row("POST ", "Missing request target"),
                row("POST  / HTTP/1.1", "Invalid request target: Illegal character in authority at index 0: ' /'"),
                row("/Hi /", "Invalid method name: illegal character at index 0: '/Hi'"),
                row("POST /hi HTTP/1.2", "Unknown HTTP version"),
                row("POST /hi HTTP/1.2 HTTP/1.1",
                        "Invalid request target: Illegal character in path at index 3: '/hi HTTP/1.2'"))

        forAll(table) { requestLine, expectedError ->
            val error = shouldThrow<InvalidHttpRequest> { metadataParser.parseRequestLine(requestLine) }
            error.message shouldBe expectedError
            error.lineNumber shouldBe if (requestLine.isEmpty()) 0 else 1
        }
    }

    @Test
    fun canParseLegalRequestLineStrict() {
        val table = table(
                headers("Request line", "Expected version", "Expected method", "Expected path", "Expected String"),
                row("POST /hello HTTP/1.1", HttpVersion.HTTP_1_1, "POST", "/hello", "POST /hello HTTP/1.1"),
                row("do /hello HTTP/1.0", HttpVersion.HTTP_1_0, "do", "/hello", "do /hello HTTP/1.0"),
                row("PUT /test/test%40example.com/test HTTP/1.1", HttpVersion.HTTP_1_1, "PUT", "/test/test@example.com/test", "PUT /test/test%40example.com/test HTTP/1.1"),
                row("PUT /test/test@example.org/test HTTP/1.1", HttpVersion.HTTP_1_1, "PUT", "/test/test@example.org/test", "PUT /test/test@example.org/test HTTP/1.1"),
                row("GET /test/test%3Ffoo%3Dbar%26a%3Db/test HTTP/1.0", HttpVersion.HTTP_1_0, "GET", "/test/test?foo=bar&a=b/test", "GET /test/test%3Ffoo%3Dbar%26a%3Db/test HTTP/1.0"),
                row("GET /hello?field=encoded%20value HTTP/1.0", HttpVersion.HTTP_1_0, "GET", "/hello",
                        "GET /hello?field=encoded%20value HTTP/1.0"),
                row("GET /hello?a=1&b=2 HTTP/1.0", HttpVersion.HTTP_1_0, "GET", "/hello", "GET /hello?a=1&b=2 HTTP/1.0"))

        forAll(table) { requestLine, expectedVersion, expectedMethod, expectedPath, expectedString ->
            try {
                strictMetadataParser.parseRequestLine(requestLine).run {
                    httpVersion shouldBe expectedVersion
                    method shouldBe expectedMethod
                    uri.path shouldBe expectedPath
                    toString() shouldBe expectedString
                }
            } catch (e: InvalidHttpRequest) {
                fail(e.toString())
            }
        }
    }

    @Test
    fun cannotParseIllegalRequestLine__strict() {
        val table = table(headers("Request line", "Expected error"),
                row("", "No content"),
                row("/", "Invalid request line: '/'"),
                row("GET", "Invalid request line: 'GET'"),
                row("POST  ", "Unknown HTTP version"),
                row("POST  / HTTP/1.1", "Invalid request target: Illegal character in authority at index 0: ' /'"),
                row("GET /", "Missing HTTP version"),
                row("POST /hello", "Missing HTTP version"),
                row("/Hi / HTTP/1.1", "Invalid method name: illegal character at index 0: '/Hi'"),
                row("GÅ / HTTP/1.1", "Invalid method name: illegal character at index 1: 'GÅ'"),
                row("GET /hello?field=encoded value HTTP/1.0",
                        "Invalid request target: Illegal character in query at index 20: '/hello?field=encoded value'"),
                row("POST /hi HTTP/1.2", "Unknown HTTP version"),
                row("POST /hi HTTP/1.2 HTTP/1.1",
                        "Invalid request target: Illegal character in path at index 3: '/hi HTTP/1.2'"))

        forAll(table) { requestLine, expectedError ->
            val error = shouldThrow<InvalidHttpRequest> { strictMetadataParser.parseRequestLine(requestLine) }
            error.message shouldBe expectedError
            error.lineNumber shouldBe if (requestLine.isEmpty()) 0 else 1
        }
    }

    @Test
    fun canParseEncodedQueries() {
        val table = table(headers("Request line", "raw query", "decoded query", "Expected String"),
                row("GET /hi?a=1", "a=1", "a=1", "GET /hi?a=1 HTTP/1.1"),
                row("GET /hi?a=1&b=2", "a=1&b=2", "a=1&b=2", "GET /hi?a=1&b=2 HTTP/1.1"),
                row("GET /hi?a=hi%20w", "a=hi%20w", "a=hi w", "GET /hi?a=hi%20w HTTP/1.1"),
                row("GET /hi?a=foo%20%26%20bar", "a=foo%20%26%20bar", "a=foo & bar", "GET /hi?a=foo%20%26%20bar HTTP/1.1"),
                row("GET /hi?%2F%2Fencoded%3Fa%3Db%26c%3Dd&json=%7B%22a%22%3A%20null%7D",
                        "%2F%2Fencoded%3Fa%3Db%26c%3Dd&json=%7B%22a%22%3A%20null%7D",
                        "//encoded?a=b&c=d&json={\"a\": null}",
                        "GET /hi?%2F%2Fencoded%3Fa%3Db%26c%3Dd&json=%7B%22a%22%3A%20null%7D HTTP/1.1")
        )

        forAll(table) { requestLine, expectedRawQuery, expectedDecodedQuery, expectedString ->
            metadataParser.parseRequestLine(requestLine).run {
                uri.rawQuery shouldBe expectedRawQuery
                uri.query shouldBe expectedDecodedQuery
                toString() shouldBe expectedString
            }
        }
    }

    @Test
    fun requestLineWithHostPreservesEncoding() {
        val table = table(headers("Request line", "New Host", "Path", "Decoded Query", "Raw Query"),
                row("GET foo.com", "bar.com", "", "", ""),
                row("GET foo.com:90", "bar.com", "", "", ""),
                row("GET foo.com/foo", "bar.com", "/foo", "", ""),
                row("GET foo.com/foo%20bar", "example.com", "/foo bar", "", ""),
                row("GET foo.com/foo?bar", "bar.com", "/foo", "bar", "bar"),
                row("GET foo.com:90/foo?bar", "bar.com", "/foo", "bar", "bar"),
                row("GET user@foo.com:90/foo?bar", "bar.com", "/foo", "bar", "bar"),
                row("GET https://user@foo.com:90/foo?bar", "bar.com", "/foo", "bar", "bar"),
                row("GET foo.com/foo?bar=10", "a.org", "/foo", "bar=10", "bar=10"),
                row("GET foo.com/foo?foo%20bar", "a.org", "/foo", "foo bar", "foo%20bar"),
                row("GET foo.com/foo%20a?foo%20bar", "a.org", "/foo a", "foo bar", "foo%20bar"),
                row("GET foo.com/foo%20a?foo%20bar%26or&b", "a.org", "/foo a", "foo bar&or&b", "foo%20bar%26or&b"),
                row("GET http://hello?and%26you&me", "c.org", "", "and&you&me", "and%26you&me")
        )

        forAll(table) { requestLine, newHost, expectedPath, expectedQuery, expectedRawQuery ->
            metadataParser.parseRequestLine(requestLine).withHost(newHost).run {
                uri.host shouldBe newHost
                (uri.path ?: "") shouldBe expectedPath
                (uri.query ?: "") shouldBe expectedQuery
                (uri.rawQuery ?: "") shouldBe expectedRawQuery
            }
        }
    }

    @Test
    fun testConnectSetsAuthorityFormTarget() {
        val table = table(headers("method", "uri", "version", "expected request line"),
                row("CONNECT", URI("http://example.com"), HttpVersion.HTTP_1_1, "CONNECT example.com HTTP/1.1"),
                row("CONNECT", URI("http://example.com:80"), HttpVersion.HTTP_1_1, "CONNECT example.com:80 HTTP/1.1"),
                row("CONNECT", URI("http://user:pass@example.com:80"), HttpVersion.HTTP_1_1, "CONNECT example.com:80 HTTP/1.1"),
                row("CONNECT", URI("http://example.com/somePath"), HttpVersion.HTTP_1_1, "CONNECT example.com HTTP/1.1"),
                row("CONNECT", URI("http://example.com:80"), HttpVersion.HTTP_1_1, "CONNECT example.com:80 HTTP/1.1"),
                row("CONNECT", URI("http://www.example.com:80"), HttpVersion.HTTP_1_1, "CONNECT www.example.com:80 HTTP/1.1"),
        )
        forAll(table) { method, uri, version, expectedRequest ->
            RequestLine(method, uri, version).run {
                toString() shouldBe expectedRequest
            }
        }
    }

}