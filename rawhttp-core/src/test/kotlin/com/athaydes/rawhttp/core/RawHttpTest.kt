package com.athaydes.rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets.UTF_8

fun Any.fileFromResource(resource: String): File {
    val file = File.createTempFile("raw-http", "txt")
    file.writeBytes(this.javaClass.getResource(resource).readBytes())
    return file
}

class SimpleHttpRequestTests : StringSpec({

    "Should be able to parse simplest HTTP Request" {
        RawHttp().parseRequest("GET localhost:8080").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldEqual URI.create("http://localhost:8080")
            toString() shouldBe "GET / HTTP/1.1\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldEqual mapOf("HOST" to listOf("localhost"))
            body should notBePresent()
        }
    }

    "Should be able to parse HTTP Request with path and HTTP version" {
        RawHttp().parseRequest("GET https://localhost:8080/my/resource/234 HTTP/1.0").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            uri shouldEqual URI.create("https://localhost:8080/my/resource/234")
            toString() shouldBe "GET /my/resource/234 HTTP/1.0\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body should notBePresent()
        }
    }

    "Should be able to parse HTTP Request with path, query and fragment" {
        RawHttp().parseRequest("GET https://localhost:8080/resource?start=0&limit=10#blah").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldEqual URI.create("https://localhost:8080/resource?start=0&limit=10#blah")
            toString() shouldBe "GET /resource?start=0&limit=10 HTTP/1.1\r\nHost: localhost\r\n\r\n"
            headers.asMap() shouldBe mapOf("HOST" to listOf("localhost"))
            body should notBePresent()
        }
    }

    "Uses Host header to identify target server if missing from method line" {
        RawHttp().parseRequest("GET /hello\nHost: www.example.com").eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldEqual URI.create("http://www.example.com/hello")
            toString() shouldBe "GET /hello HTTP/1.1\r\nHost: www.example.com\r\n\r\n"
            headers.asMap() shouldEqual mapOf("HOST" to listOf("www.example.com"))
            body should notBePresent()
        }
    }

    "Request can have a body" {
        RawHttp().parseRequest("""
            POST http://host.com/myresource/123456
            Content-Type: application/json
            Content-Length: 48
            Accept: text/html

            {
                "hello": true,
                "from": "kotlin-test"
            }
            """.trimIndent()).eagerly().run {
            val expectedBody = "{\n    \"hello\": true,\n    \"from\": \"kotlin-test\"\n}"

            method shouldBe "POST"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldEqual URI.create("http://host.com/myresource/123456")
            toString() shouldEqual "POST /myresource/123456 HTTP/1.1\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: 48\r\n" +
                    "Accept: text/html\r\n" +
                    "Host: host.com\r\n\r\n" +
                    expectedBody
            headers.asMap() shouldEqual mapOf(
                    "HOST" to listOf("host.com"),
                    "CONTENT-TYPE" to listOf("application/json"),
                    "CONTENT-LENGTH" to listOf("48"),
                    "ACCEPT" to listOf("text/html"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual expectedBody
                it.asBytes().size shouldEqual 48
            }
        }
    }

    "Should be able to parse HTTP Request with path and HTTP version from a file" {
        val requestFile = fileFromResource("simple.request")

        RawHttp().parseRequest(requestFile).eagerly().run {
            method shouldBe "GET"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldEqual URI.create("http://example.com/resources/abcde")
            toString() shouldEqual "GET /resources/abcde HTTP/1.1\r\n" +
                    "Accept: application/json\r\n" +
                    "Host: example.com\r\n\r\n"
            headers.asMap() shouldEqual mapOf(
                    "ACCEPT" to listOf("application/json"),
                    "HOST" to listOf("example.com"))
            body should notBePresent()
        }
    }

    "Should be able to parse HTTP Request with body from a file" {
        val requestFile = fileFromResource("post.request")

        RawHttp().parseRequest(requestFile).eagerly().run {
            method shouldBe "POST"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            uri shouldEqual URI.create("https://example.com/my-resource/SDFKJWEKLKLKWERLWKEGJGJE")
            headers.asMap() shouldEqual mapOf(
                    "ACCEPT" to listOf("text/plain", "*/*"),
                    "CONTENT-TYPE" to listOf("text/encrypted"),
                    "CONTENT-LENGTH" to listOf("765"),
                    "USER-AGENT" to listOf("rawhttp"),
                    "HOST" to listOf("example.com"))
            body should bePresent {
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

                it.asString(UTF_8) shouldEqual expectedBody
                it.asBytes().size shouldEqual 765
            }
        }
    }

})

class SimpleHttpResponseTests : StringSpec({

    "Should be able to parse simplest HTTP Response" {
        RawHttp().parseResponse("HTTP/1.0 404 NOT FOUND").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_0
            startLine.statusCode shouldBe 404
            startLine.reason shouldEqual "NOT FOUND"
            toString() shouldEqual "HTTP/1.0 404 NOT FOUND\r\n\r\n"
            headers.headerNames should beEmpty()
            body should bePresent { it.toString() shouldEqual "" }
        }
    }

    "Should be able to parse HTTP Response that may not have a body" {
        RawHttp().parseResponse("HTTP/1.1 100 CONTINUE").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 100
            startLine.reason shouldEqual "CONTINUE"
            toString() shouldEqual "HTTP/1.1 100 CONTINUE\r\n\r\n"
            headers.headerNames should beEmpty()
            body should notBePresent()
        }
    }

    "Should be able to parse simple HTTP Response with body" {
        RawHttp().parseResponse("HTTP/1.1 200 OK\r\nServer: Apache\r\n\r\nHello World!").eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 200
            startLine.reason shouldEqual "OK"
            toString() shouldEqual "HTTP/1.1 200 OK\r\nServer: Apache\r\n\r\nHello World!"
            headers.asMap() shouldEqual mapOf("SERVER" to listOf("Apache"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual "Hello World!"
            }
        }
    }

    "Should be able to parse longer HTTP Response with invalid line-endings" {
        RawHttp().parseResponse("""
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
        """.trimIndent()).eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 200
            startLine.reason shouldEqual "OK"
            toString() shouldEqual "HTTP/1.1 200 OK\r\n" +
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
            headers.asMap() shouldEqual mapOf(
                    "DATE" to listOf("Mon, 27 Jul 2009 12:28:53 GMT"),
                    "SERVER" to listOf("Apache"),
                    "LAST-MODIFIED" to listOf("Wed, 22 Jul 2009 19:15:56 GMT"),
                    "ETAG" to listOf("\"34aa387-d-1568eb00\""),
                    "ACCEPT-RANGES" to listOf("bytes"),
                    "CONTENT-LENGTH" to listOf("39"),
                    "VARY" to listOf("Accept-Encoding"),
                    "CONTENT-TYPE" to listOf("application/json")
            )
            body should bePresent {
                it.asString(UTF_8) shouldEqual "{\n  \"hello\": \"world\",\n  \"number\": 123\n}"
            }
        }
    }

    "Should be able to parse HTTP Response from file" {
        val responseFile = fileFromResource("simple.response")

        RawHttp().parseResponse(responseFile).eagerly().run {
            val expectedBody = "{\n  \"message\": \"Hello World\",\n  \"language\": \"EN\"\n}"

            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 200
            startLine.reason shouldEqual "OK"
            toString() shouldEqual "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json\r\n" +
                    "Content-Length: 50\r\n" +
                    "Server: super-server\r\n\r\n" +
                    expectedBody
            headers.asMap() shouldEqual mapOf(
                    "SERVER" to listOf("super-server"),
                    "CONTENT-TYPE" to listOf("application/json"),
                    "CONTENT-LENGTH" to listOf("50"))
            body should bePresent {
                it.asString(UTF_8) shouldEqual expectedBody
            }
        }
    }

    "Should ignore body of HTTP Response that may not have a body" {
        val stream = "HTTP/1.1 304 Not Modified\r\nETag: 12345\r\n\r\nBODY".byteInputStream()

        RawHttp().parseResponse(stream).eagerly().run {
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.statusCode shouldBe 304
            startLine.reason shouldEqual "Not Modified"
            headers.asMap() shouldEqual mapOf("ETAG" to listOf("12345"))
            body should notBePresent()
        }

        // verify that the stream was only consumed until the empty-line after the last header
        String(stream.readBytes(4)) shouldEqual "BODY"
    }

})

class CopyHttpRequestTests : StringSpec({

    "Can make a copy of a HTTP Request with added headers" {
        val addedHeaders = RawHttpHeaders.Builder.newBuilder()
                .with("Accept", "any/thing")
                .with("User-Agent", "raw-http")
                .build()

        RawHttp().parseRequest("GET /hello\nHost: www.example.com")
                .withHeaders(addedHeaders).run {
                    method shouldEqual "GET"
                    uri.path shouldEqual "/hello"
                    headers["Host"] shouldEqual listOf("www.example.com")
                    headers["Accept"] shouldEqual listOf("any/thing")
                    headers["User-Agent"] shouldEqual listOf("raw-http")
                    headers.asMap().size shouldEqual 3
                    senderAddress should notBePresent()
                }

    }

})

class CopyHttpResponseTests : StringSpec({

    "Can make a copy of a HTTP Response with added headers" {
        val addedHeaders = RawHttpHeaders.Builder.newBuilder()
                .with("Accept", "any/thing")
                .with("User-Agent", "raw-http")
                .build()

        RawHttp().parseResponse("HTTP/1.1 200 OK\nHost: www.example.com")
                .withHeaders(addedHeaders).run {
                    startLine.httpVersion shouldEqual HttpVersion.HTTP_1_1
                    statusCode shouldEqual 200
                    startLine.reason shouldEqual "OK"
                    libResponse should notBePresent()
                    headers["Host"] shouldEqual listOf("www.example.com")
                    headers["Accept"] shouldEqual listOf("any/thing")
                    headers["User-Agent"] shouldEqual listOf("raw-http")
                    headers.asMap().size shouldEqual 3
                }

    }

})
