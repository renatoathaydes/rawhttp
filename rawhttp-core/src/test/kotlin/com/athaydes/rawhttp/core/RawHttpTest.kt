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
            uri shouldEqual URI.create("localhost:8080")
            headers.keys should beEmpty()
            body.isPresent shouldBe false
        }
    }

    "Should be able to parse HTTP Request with path and HTTP version" {
        RawHttp().parseRequest("GET https://localhost:8080/my/resource/234 HTTP/1.0").run {
            method shouldBe "GET"
            httpVersion shouldBe "HTTP/1.0"
            uri shouldEqual URI.create("https://localhost:8080/my/resource/234")
            headers.keys should beEmpty()
            body.isPresent shouldBe false
        }
    }

    "Uses Host header to identify target server if missing from method line" {
        RawHttp().parseRequest("GET /hello\nHost: www.example.com").run {
            method shouldBe "GET"
            httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://www.example.com/hello")
            headers shouldEqual mapOf("Host" to listOf("www.example.com"))
            body.isPresent shouldBe false
        }
    }

})