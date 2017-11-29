package com.athaydes.rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import java.net.URI

class SimpleHttpRequestTests : StringSpec({

    "Should be able to parse simplest HTTP Request" {
        RawHttp().parseRequest("GET localhost:8080").run {
            method shouldBe "GET"
            httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://localhost:8080")
            headers shouldEqual mapOf("Host" to listOf("localhost"))
            body.eager().asBytes().toList() should beEmpty()
        }
    }

    "Should be able to parse HTTP Request with path and HTTP version" {
        RawHttp().parseRequest("GET https://localhost:8080/my/resource/234 HTTP/1.0").run {
            method shouldBe "GET"
            httpVersion shouldBe "HTTP/1.0"
            uri shouldEqual URI.create("https://localhost:8080/my/resource/234")
            headers.keys should beEmpty()
            body.eager().asBytes().toList()
        }
    }

    "Uses Host header to identify target server if missing from method line" {
        RawHttp().parseRequest("GET /hello\nHost: www.example.com").run {
            method shouldBe "GET"
            httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://www.example.com/hello")
            headers shouldEqual mapOf("Host" to listOf("www.example.com"))
            body.eager().asBytes().toList()
        }
    }

    "Request can have a body" {
        RawHttp().parseRequest("""
            POST http://host.com/myresource/123456
            Content-Type: application/json
            Accept: text/html

            {
                "hello": true,
                "from": "kotlin-test"
            }
            """.trimIndent()).run {
            method shouldBe "POST"
            httpVersion shouldBe "HTTP/1.1"
            uri shouldEqual URI.create("http://host.com/myresource/123456")
            headers shouldEqual mapOf(
                    "Host" to listOf("host.com"),
                    "Content-Type" to listOf("application/json"),
                    "Accept" to listOf("text/html"))
            String(body.eager().asBytes()) shouldEqual "{\n    \"hello\": true,\n    \"from\": \"kotlin-test\"\n}"
        }
    }

})

class SimpleHttpResponseTests : StringSpec({

    "Should be able to parse simplest HTTP Response" {
        RawHttp().parseResponse("HTTP/1.0 404 NOT FOUND").run {
            statusCodeLine.httpVersion shouldBe "HTTP/1.0"
            statusCodeLine.statusCode shouldBe 404
            statusCodeLine.reason shouldEqual "NOT FOUND"
            headers.keys should beEmpty()
            bodyReader.eager().asBytes().toList() should beEmpty()
        }
    }

    "Should be able to parse simple HTTP Response with body" {
        RawHttp().parseResponse("HTTP/1.1 200 OK\r\nServer: Apache\r\n\r\nHello World!".trimIndent()).run {
            statusCodeLine.httpVersion shouldBe "HTTP/1.1"
            statusCodeLine.statusCode shouldBe 200
            statusCodeLine.reason shouldEqual "OK"
            headers shouldEqual mapOf("Server" to listOf("Apache"))
            String(bodyReader.eager().asBytes()) shouldEqual "Hello World!"
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
             Content-Length: 51
             Vary: Accept-Encoding
             Content-Type: application/json

             {
               "hello": "world",
               "number": 123
             }
        """.trimIndent()).run {
            statusCodeLine.httpVersion shouldBe "HTTP/1.1"
            statusCodeLine.statusCode shouldBe 200
            statusCodeLine.reason shouldEqual "OK"
            headers shouldEqual mapOf(
                    "Date" to listOf("Mon, 27 Jul 2009 12:28:53 GMT"),
                    "Server" to listOf("Apache"),
                    "Last-Modified" to listOf("Wed, 22 Jul 2009 19:15:56 GMT"),
                    "ETag" to listOf("\"34aa387-d-1568eb00\""),
                    "Accept-Ranges" to listOf("bytes"),
                    "Content-Length" to listOf("51"),
                    "Vary" to listOf("Accept-Encoding"),
                    "Content-Type" to listOf("application/json")
            )
            String(bodyReader.eager().asBytes()) shouldEqual "{\n  \"hello\": \"world\",\n  \"number\": 123\n}"
        }
    }

})