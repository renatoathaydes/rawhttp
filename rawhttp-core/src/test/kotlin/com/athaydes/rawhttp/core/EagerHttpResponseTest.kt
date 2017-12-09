package com.athaydes.rawhttp.core

import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec

class EagerHttpResponseTest: StringSpec({

    "Body trailer headers are added to the response headers" {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\n"

        RawHttp().parseResponse("HTTP/1.1 200 OK\r\n" +
                "Content-Type: http/response\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                body).eagerly().run {
            headers.asMap() shouldEqual mapOf(
                    "CONTENT-TYPE" to listOf("http/response"),
                    "TRANSFER-ENCODING" to listOf("chunked"),
                    "HELLO" to listOf("hi there", "wow"),
                    "BYE" to listOf("true")
            )
        }
    }

})