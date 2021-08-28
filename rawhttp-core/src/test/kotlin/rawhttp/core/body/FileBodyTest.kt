package rawhttp.core.body

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import rawhttp.core.HttpVersion
import rawhttp.core.RawHttp
import rawhttp.core.fileFromResource
import rawhttp.core.shouldHaveSameElementsAs
import java.net.URI

class FileBodyTest : StringSpec({

    "It is possible to add a file body to a HTTP Request with no body" {
        val request = RawHttp().parseRequest("PUT localhost:8080/404")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        request.withBody(fileBody).eagerly().run {
            method shouldBe "PUT"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://localhost:8080/404")
            headers.asMap() shouldBe mapOf(
                    "CONTENT-LENGTH" to listOf(fileBody.file.length().toString()),
                    "HOST" to listOf("localhost"),
                    "CONTENT-TYPE" to listOf("image/png"))
            body shouldBePresent { it.asRawBytes() shouldHaveSameElementsAs fileBody.file.readBytes() }
        }
    }

    "It is possible to replace the body of a HTTP Request with a file body" {
        val request = RawHttp().parseRequest("PUT localhost:8080/404\r\n" +
                "Content-Length: 4\r\n\r\n" +
                "ABCD")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        request.withBody(fileBody).eagerly().run {
            method shouldBe "PUT"
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1 // the default
            uri shouldBe URI.create("http://localhost:8080/404")
            headers.asMap() shouldBe mapOf(
                    "CONTENT-LENGTH" to listOf(fileBody.file.length().toString()),
                    "HOST" to listOf("localhost"),
                    "CONTENT-TYPE" to listOf("image/png"))
            body shouldBePresent { it.asRawBytes() shouldHaveSameElementsAs fileBody.file.readBytes() }
        }
    }

    "It is possible to add a file body to a HTTP Response with no body" {
        val response = RawHttp().parseResponse("HTTP/1.1 200 OK\r\nServer: Apache")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        response.withBody(fileBody).eagerly().run {
            statusCode shouldBe 200
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.reason shouldBe "OK"
            headers.asMap() shouldBe mapOf(
                    "CONTENT-LENGTH" to listOf(fileBody.file.length().toString()),
                    "SERVER" to listOf("Apache"),
                    "CONTENT-TYPE" to listOf("image/png"))
            body shouldBePresent { it.asRawBytes() shouldHaveSameElementsAs fileBody.file.readBytes() }
        }
    }

    "It is possible to replace the body of a HTTP Response with a file body" {
        val response = RawHttp().parseResponse("HTTP/1.1 201 CREATED\r\n" +
                "Content-Length: 4\r\n\r\n" +
                "ABCD")

        val fileBody = FileBody(fileFromResource("404.png"), "image/png")

        response.withBody(fileBody).eagerly().run {
            statusCode shouldBe 201
            startLine.httpVersion shouldBe HttpVersion.HTTP_1_1
            startLine.reason shouldBe "CREATED"
            headers.asMap() shouldBe mapOf(
                    "CONTENT-LENGTH" to listOf(fileBody.file.length().toString()),
                    "CONTENT-TYPE" to listOf("image/png"))
            body shouldBePresent { it.asRawBytes() shouldHaveSameElementsAs fileBody.file.readBytes() }
        }
    }

})