package rawhttp.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.optional.bePresent
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

fun Any.fileFromResource(resource: String): File {
    val file = File.createTempFile("raw-http", "txt")
    file.writeBytes(this.javaClass.getResource(resource)!!.readBytes())
    return file
}

class SimpleHttpRequestTests {

    @Test
    fun `Should be able to parse simplest HTTP Request`() {
        RawHttp().parseRequest("GET localhost:8080").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://localhost:8080")
            toString() shouldBe "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Should be able to parse HTTP Request with path and HTTP version`() {
        RawHttp().parseRequest("GET https://localhost:8080/my/resource/234 HTTP/1.0").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            uri shouldBe URI.create("https://localhost:8080/my/resource/234")
            toString() shouldBe "GET /my/resource/234 HTTP/1.0\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Should be able to parse HTTP Request with path, query and fragment`() {
        RawHttp().parseRequest("GET https://localhost:8080/resource?start=0&limit=10#blah").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldBe URI.create("https://localhost:8080/resource?start=0&limit=10#blah")
            toString() shouldBe "GET /resource?start=0&limit=10 HTTP/1.1\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Can parse encoded HTTP Request query`() {
        RawHttp().parseRequest("GET /hello?field=encoded%20value HTTP/1.0\nHost: www.example.com").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            uri.path shouldBe "/hello"
            uri.query shouldBe "field=encoded value"
            toString() shouldBe "GET /hello?field=encoded%20value HTTP/1.0\r\nHost: www.example.com\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("www.example.com"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Uses Host header to identify target server if missing from method line`() {
        RawHttp().parseRequest("GET /hello\nHost: www.example.com").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://www.example.com/hello")
            toString() shouldBe "GET /hello HTTP/1.1\r\nHost: www.example.com\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("www.example.com"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Uses request-line URI Host instead of Host header to identify target server if both are given`() {
        RawHttp().parseRequest("GET http://favorite.host/hello\nHost: www.example.com").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://favorite.host/hello")
            toString() shouldBe "GET /hello HTTP/1.1\r\nHost: www.example.com\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("www.example.com"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Request can have a body`() {
        RawHttp().parseRequest(
            """
            POST http://host.com/myresource/123456
            Content-Type: application/json
            Content-Length: 48
            Accept: text/html

            {
                "hello": true,
                "from": "kotlin-test"
            }
            """.trimIndent()
        ).eagerly().run {
            val expectedBody = "{\n    \"hello\": true,\n    \"from\": \"kotlin-test\"\n}"

            method shouldBe "POST"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldBe URI.create("http://host.com/myresource/123456")
            toString() shouldBe "POST /myresource/123456 HTTP/1.1\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: 48\r\n" +
                    "Accept: text/html\r\n" +
                    "Host: host.com\r\n\r\n" +
                    expectedBody
            headers.asMap() shouldBe mapOf(
                "HOST" to listOf("host.com"),
                "CONTENT-TYPE" to listOf("application/json"),
                "CONTENT-LENGTH" to listOf("48"),
                "ACCEPT" to listOf("text/html")
            )
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBe expectedBody
                it.asRawBytes().size shouldBe 48
            }
        }
    }

    @Test
    fun `Should be able to parse HTTP Request with path and HTTP version from a file`() {
        val requestFile = fileFromResource("simple.request")

        RawHttp().parseRequest(requestFile).eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldBe URI.create("http://example.com/resources/abcde")
            toString() shouldBe "GET /resources/abcde HTTP/1.1\r\n" +
                    "Accept: application/json\r\n" +
                    "Host: example.com\r\n\r\n"
            headers.asMap() shouldBe mapOf(
                "ACCEPT" to listOf("application/json"),
                "HOST" to listOf("example.com")
            )
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Should be able to parse HTTP Request with body from a file`() {
        val requestFile = fileFromResource("post.request")

        RawHttp().parseRequest(requestFile).eagerly().run {
            method shouldBe "POST"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldBe URI.create("https://example.com/my-resource/SDFKJWEKLKLKWERLWKEGJGJE")
            headers.asMap() shouldBe mapOf(
                "ACCEPT" to listOf("text/plain", "*/*"),
                "CONTENT-TYPE" to listOf("text/encrypted"),
                "CONTENT-LENGTH" to listOf("765"),
                "USER-AGENT" to listOf("rawhttp"),
                "HOST" to listOf("example.com")
            )
            body shouldBePresent {
                val expectedBody = "BEGIN KEYBASE SALTPACK ENCRYPTED MESSAGE. " +
                        "kiOUtMhcc4NXXRb XMxIeCbf5rCmoNO Z9cuk3vFu4WUHGE FbP7OCGjWcildtW gRRS2oOGl0tDgNc " +
                        "yZBlB9lxbNQs77O RLN5mMqTNWbKrwQ mSZolwGEonepkkk seiN0mXd8vwWM9S 7ssjvDZGbGjAfdO " +
                        "AUJmEHLdsRKrmUX yGqKzFKkG9XuiX9 8odcxJUhBMuUAUT dPpaL3sntmQTWal FfD5rj2o0ysBE92 " +
                        "lQjYk9Sok2Ofjod ytMjCDOF0eowY67 TgdmD9xmjC9kt0N v3XJB8FQA6mntYY QvTGvMyEInxfyd0 " +
                        "4GnXi1PgbwwH9O4 Ntyrt73xVko2RdV 7yaEPrSxveTEQMh P5RxWbTqXsNNagf UfgvsZlpJFxKlPs " +
                        "DxovufvUTamC5G8 Hq5XtAT811RZlro rXjZmgoS2uUinRO 0BCq3LujBBrEzQS vV4ZV6DroIjJ6kz " +
                        "fm0sr8nIZ4pdUVS qNi5LhWIgGwPlg1 KKIOuv6aCFLUFtO pYzmPXilv7ntnES 88EnMhI1wPLDiih " +
                        "Cy1LQyPzT7gUM3A josP5Nne89rWCD9 QrKxhczapyUSch4 E4qqihxkujRPqEu toCyI5eKEnvVbfn " +
                        "ldCLQWSoA7RLYRZ E8x3TY7EqFJpmLP iulp9YqVZj. END KEYBASE SALTPACK ENCRYPTED MESSAGE."

                it.asRawString(UTF_8) shouldBe expectedBody
                it.asRawBytes().size shouldBe 765
            }
        }
    }

    @Test
    fun `Should be able to parse HTTP Request with trailing new-line as recommended for robustness`() {
        RawHttp().parseRequest("\r\nGET localhost:8080").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://localhost:8080")
            toString() shouldBe "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body shouldNot bePresent()
        }
        RawHttp().parseRequest("\nGET localhost:8080").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://localhost:8080")
            toString() shouldBe "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body shouldNot bePresent()
        }
    }

    @Test
    fun `May allow request to have shorter body than expected`() {
        val defaultHttp = RawHttp()
        val lenientHttp = RawHttp(
            RawHttpOptions.newBuilder()
                .allowContentLengthMismatch()
                .build()
        )

        val requestWithTooShortBody = "POST /foo HTTP/1.1\n" +
                "Host: example.org\n" +
                "Content-Length: 10\n\n" +
                "short"

        val defaultRequest = defaultHttp.parseRequest(requestWithTooShortBody)
        val lenientRequest = lenientHttp.parseRequest(requestWithTooShortBody)

        // by default, an Exception is thrown
        defaultRequest.body shouldBePresent {
            shouldThrow<IOException> { decodeBodyToString(UTF_8) }
        }

        // but the lenient RawHTTP allows mismatches
        lenientRequest.body.shouldBePresent {
            decodeBodyToString(UTF_8) shouldBe "short"
        }
    }

}

class SimpleHttpResponseTests {

    @Test
    fun `Should be able to parse simplest HTTP Response`() {
        RawHttp().parseResponse("404").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            startLine.statusCode shouldBe 404
            startLine.reason shouldBe ""
            toString() shouldBe "HTTP/1.1 404\r\n\r\n"
            headers.headerNames shouldHaveSize 0
            body shouldBePresent { it.toString() shouldBe "" }
        }
    }

    @Test
    fun `Should be able to parse simple HTTP Response`() {
        RawHttp().parseResponse("HTTP/1.0 404 NOT FOUND").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            startLine.statusCode shouldBe 404
            startLine.reason shouldBe "NOT FOUND"
            toString() shouldBe "HTTP/1.0 404 NOT FOUND\r\n\r\n"
            headers.headerNames shouldHaveSize 0
            body shouldBePresent { it.toString() shouldBe "" }
        }
    }

    @Test
    fun `Should be able to parse HTTP Response that may not have a body`() {
        RawHttp().parseResponse("HTTP/1.1 100 CONTINUE").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 100
            startLine.reason shouldBe "CONTINUE"
            toString() shouldBe "HTTP/1.1 100 CONTINUE\r\n\r\n"
            headers.headerNames shouldHaveSize 0
            body shouldNot bePresent()
        }
    }

    @Test
    fun `Should be able to parse simple HTTP Response with body`() {
        RawHttp().parseResponse("HTTP/1.1 200 OK\r\nServer: Apache\r\n\r\nHello World!").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 200
            startLine.reason shouldBe "OK"
            toString() shouldBe "HTTP/1.1 200 OK\r\nServer: Apache\r\n\r\nHello World!"
            headers.asMap() shouldBe mapOf("SERVER" to listOf("Apache"))
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBe "Hello World!"
            }
        }
    }

    @Test
    fun `Should be able to parse longer HTTP Response with invalid line-endings`() {
        RawHttp().parseResponse(
            """
             HTTP/1.1 200 OK
             Date: Mon, 27 Jul 2009 12:28:53 GMT
             Server: Apache
             Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT
             ETag: "34aa387-d-1568eb00"
             Accept-Ranges: bytes
             Content-Length: 39
             Vary: Accept-Encoding
             Content-Type: application/json

             {
               "hello": "world",
               "number": 123
             }
        """.trimIndent()
        ).eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 200
            startLine.reason shouldBe "OK"
            toString() shouldBe "HTTP/1.1 200 OK\r\n" +
                    "Date: Mon, 27 Jul 2009 12:28:53 GMT\r\n" +
                    "Server: Apache\r\n" +
                    "Last-Modified: Wed, 22 Jul 2009 19:15:56 GMT\r\n" +
                    "ETag: \"34aa387-d-1568eb00\"\r\n" +
                    "Accept-Ranges: bytes\r\n" +
                    "Content-Length: 39\r\n" +
                    "Vary: Accept-Encoding\r\n" +
                    "Content-Type: application/json\r\n\r\n" +
                    "{\n" +
                    "  \"hello\": \"world\",\n" +
                    "  \"number\": 123\n" +
                    "}"
            headers.asMap() shouldBe mapOf(
                "DATE" to listOf("Mon, 27 Jul 2009 12:28:53 GMT"),
                "SERVER" to listOf("Apache"),
                "LAST-MODIFIED" to listOf("Wed, 22 Jul 2009 19:15:56 GMT"),
                "ETAG" to listOf("\"34aa387-d-1568eb00\""),
                "ACCEPT-RANGES" to listOf("bytes"),
                "CONTENT-LENGTH" to listOf("39"),
                "VARY" to listOf("Accept-Encoding"),
                "CONTENT-TYPE" to listOf("application/json")
            )
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBe "{\n  \"hello\": \"world\",\n  \"number\": 123\n}"
            }
        }
    }

    @Test
    fun `Should be able to parse HTTP Response from file`() {
        val responseFile = fileFromResource("simple.response")

        RawHttp().parseResponse(responseFile).eagerly().run {
            val expectedBody = "{\n  \"message\": \"Hello World\",\n  \"language\": \"EN\"\n}"

            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 200
            startLine.reason shouldBe "OK"
            toString() shouldBe "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: 50\r\n" +
                    "Server: super-server\r\n\r\n" +
                    expectedBody
            headers.asMap() shouldBe mapOf(
                "SERVER" to listOf("super-server"),
                "CONTENT-TYPE" to listOf("application/json"),
                "CONTENT-LENGTH" to listOf("50")
            )
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBe expectedBody
            }
        }
    }

    @Test
    fun `Should ignore body of HTTP Response that may not have a body`() {
        val stream = "HTTP/1.1 304 Not Modified\r\nETag: 12345\r\n\r\nBODY".byteInputStream()

        RawHttp().parseResponse(stream).eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 304
            startLine.reason shouldBe "Not Modified"
            headers.asMap() shouldBe mapOf("ETAG" to listOf("12345"))
            body shouldNot bePresent()
        }

        // verify that the stream was only consumed until the empty-line after the last header
        String(stream.readBytes()) shouldBe "BODY"
    }

    @Test
    fun `Should be able to parse HTTP Response with trailing new-line as recommended for robustness`() {
        RawHttp().parseResponse("\r\nHTTP/1.0 404 NOT FOUND").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            startLine.statusCode shouldBe 404
            startLine.reason shouldBe "NOT FOUND"
            toString() shouldBe "HTTP/1.0 404 NOT FOUND\r\n\r\n"
            headers.headerNames shouldHaveSize 0
            body shouldBePresent { it.toString() shouldBe "" }
        }
        RawHttp().parseResponse("\nHTTP/1.0 404 NOT FOUND").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            startLine.statusCode shouldBe 404
            startLine.reason shouldBe "NOT FOUND"
            toString() shouldBe "HTTP/1.0 404 NOT FOUND\r\n\r\n"
            headers.headerNames shouldHaveSize 0
            body shouldBePresent { it.toString() shouldBe "" }
        }
    }

    /**
     * See https://www.rfc-editor.org/rfc/rfc7230#section-3.2.4
     */
    @Test
    fun `Can parse deprecated multi-line headers`() {
        SimpleHttpRequestTests::class.java.getResourceAsStream("response-headers.http")!!.use {
            RawHttp().parseResponse(it).run {
                startLine.statusCode shouldBe 200
                headers.headerNames shouldContainExactlyInAnyOrder listOf(
                    "Cache-Control",
                    "Content-Type",
                    "Set-Cookie",
                    "X-Content-Type-Options",
                    "X-Frame-Options",
                    "X-XSS-Protection",
                    "Strict-Transport-Security",
                    "Content-Security-Policy",
                    "Date",
                    "Content-Length",
                )
                headers["content-length"] shouldBe listOf("95247")
                headers.getFirst("Content-Security-Policy") shouldBePresent { csp ->
                    // we remove the first whitespace as it marks the new beginning of a line, but keep other whitespace
                    csp shouldContain "default-src 'self' www.google-analytics.com www.youtube.com;" +
                            "         child-src 'self' www.youtube.com www.youtube-nocookie.com player.vimeo.com www.google.com;"
                }
            }
        }
    }
}

class CopyHttpRequestTests {

    @Test
    fun `Can make a copy of a HTTP Request with added headers`() {
        val addedHeaders = RawHttpHeaders.newBuilder()
            .with("Accept", "any/thing")
            .with("User-Agent", "raw-http")
            .build()

        RawHttp().parseRequest("GET /hello\nHost: www.example.com")
            .withHeaders(addedHeaders).run {
                method shouldBe "GET"
                uri.path shouldBe "/hello"
                headers["Host"] shouldBe listOf("www.example.com")
                headers["Accept"] shouldBe listOf("any/thing")
                headers["User-Agent"] shouldBe listOf("raw-http")
                headers.asMap().size shouldBe 3
                senderAddress shouldNot bePresent()
            }

    }

    @Test
    fun `Can make a copy of a HTTP Request with a replaced request line (different host)`() {
        RawHttp().parseRequest("GET /hello HTTP/1.0\nHost: example.org:8080")
            .withRequestLine(RequestLine("GET", URI.create("http://localhost:8999/foo/bar"), HttpVersion.HTTP_1_1))
            .run {
                method shouldBe "GET"
                uri.scheme shouldBe "http"
                uri.host shouldBe "localhost"
                uri.port shouldBe 8999
                uri.path shouldBe "/foo/bar"
                startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
                headers["Host"] shouldBe listOf("localhost:8999")
                headers.asMap().size shouldBe 1
                senderAddress shouldNot bePresent()
            }
    }

    @Test
    fun `Can make a copy of a HTTP Request with a replaced request line (different port)`() {
        RawHttp().parseRequest("GET /hello HTTP/1.0\nHost: localhost:8080")
            .withRequestLine(RequestLine("GET", URI.create("http://localhost:8999/foo/bar"), HttpVersion.HTTP_1_1))
            .run {
                method shouldBe "GET"
                uri.scheme shouldBe "http"
                uri.host shouldBe "localhost"
                uri.port shouldBe 8999
                uri.path shouldBe "/foo/bar"
                startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
                headers["Host"] shouldBe listOf("localhost:8999")
                headers.asMap().size shouldBe 1
                senderAddress shouldNot bePresent()
            }
    }

}

class CopyHttpResponseTests {

    @Test
    fun `Can make a copy of a HTTP Response with added headers`() {
        val addedHeaders = RawHttpHeaders.newBuilder()
            .with("Accept", "any/thing")
            .with("User-Agent", "raw-http")
            .build()

        RawHttp().parseResponse("HTTP/1.1 200 OK\nHost: www.example.com")
            .withHeaders(addedHeaders).run {
                startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
                statusCode shouldBe 200
                startLine.reason shouldBe "OK"
                libResponse shouldNot bePresent()
                headers["Host"] shouldBe listOf("www.example.com")
                headers["Accept"] shouldBe listOf("any/thing")
                headers["User-Agent"] shouldBe listOf("raw-http")
                headers.asMap().size shouldBe 3
            }

    }

}
