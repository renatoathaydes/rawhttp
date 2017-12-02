package com.athaydes.rawhttp.core.file

import com.athaydes.rawhttp.core.RawHttp
import com.athaydes.rawhttp.core.bePresent
import com.athaydes.rawhttp.core.fileFromResource
import com.athaydes.rawhttp.core.shouldBe
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import java.net.URI

class FileBodyTest : StringSpec({

    "It is possible to add a file body to a HTTP Request with no body" {
        val request = RawHttp().parseRequest("PUT localhost:8080/404")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        fileBody.setFileAsBodyOf(request).eagerly().run {
            method shouldBe "PUT"
            startLine.httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://localhost:8080/404")
            headers.toMap() shouldEqual mapOf(
                    "Content-Length" to listOf(fileBody.file.length().toString()),
                    "Host" to listOf("localhost"),
                    "Content-Type" to listOf("image/png"))
            body should bePresent { it.asBytes() shouldBe fileBody.file.readBytes() }
        }
    }

    "It is possible to replace the body of a HTTP Request with a file body" {
        val request = RawHttp().parseRequest("PUT localhost:8080/404\r\n" +
                "Content-Length: 4\r\n\r\n" +
                "ABCD")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        fileBody.setFileAsBodyOf(request).eagerly().run {
            method shouldBe "PUT"
            startLine.httpVersion shouldBe "HTTP/1.1" // the default
            uri shouldEqual URI.create("http://localhost:8080/404")
            headers.toMap() shouldEqual mapOf(
                    "Content-Length" to listOf(fileBody.file.length().toString()),
                    "Host" to listOf("localhost"),
                    "Content-Type" to listOf("image/png"))
            body should bePresent { it.asBytes() shouldBe fileBody.file.readBytes() }
        }
    }

    "It is possible to add a file body to a HTTP Response with no body" {
        val response = RawHttp().parseResponse("HTTP/1.1 200 OK\r\nServer: Apache")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        fileBody.setFileAsBodyOf(response).eagerly().run {
            statusCode shouldBe 200
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.reason shouldEqual "OK"
            headers.toMap() shouldEqual mapOf(
                    "Content-Length" to listOf(fileBody.file.length().toString()),
                    "Server" to listOf("Apache"),
                    "Content-Type" to listOf("image/png"))
            body should bePresent { it.asBytes() shouldBe fileBody.file.readBytes() }
        }
    }

    "It is possible to replace the body of a HTTP Response with a file body" {
        val response = RawHttp().parseResponse("HTTP/1.1 201 CREATED\r\n" +
                "Content-Length: 4\r\n\r\n" +
                "ABCD")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        fileBody.setFileAsBodyOf(response).eagerly().run {
            statusCode shouldBe 201
            startLine.httpVersion shouldBe "HTTP/1.1"
            startLine.reason shouldEqual "CREATED"
            headers.toMap() shouldEqual mapOf(
                    "Content-Length" to listOf(fileBody.file.length().toString()),
                    "Content-Type" to listOf("image/png"))
            body should bePresent { it.asBytes() shouldBe fileBody.file.readBytes() }
        }
    }

})