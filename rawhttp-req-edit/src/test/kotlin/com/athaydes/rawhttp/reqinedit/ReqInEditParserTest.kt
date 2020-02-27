package com.athaydes.rawhttp.reqinedit

import io.kotlintest.matchers.shouldBe
import org.junit.Test
import java.io.FileNotFoundException
import java.net.URI

class ReqInEditParserTest {

    @Test
    fun canParseSimpleRequests() {
        val parser = ReqInEditParser()

        val fileLines = listOf(
                "GET /something HTTP/1.1",
                "Host: example.org",
                "Accept: text/html",
                "",
                "###   ",
                "POST /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Content-Type: application/json",
                "",
                "{\"example\": \"value\", \"count\": 1}",
                "",
                "###",
                "",
                "GET /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Accept: application/json"
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 3

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org/something")
            headers.headerNames shouldBe listOf("Host", "Accept")
            body.isPresent shouldBe false
        }
        entries[1].request.run {
            method shouldBe "POST"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Content-Type", "Content-Length")
            body.isPresent shouldBe true
            body.get().decodeBodyToString(Charsets.UTF_8) shouldBe
                    "{\"example\": \"value\", \"count\": 1}"
        }
        entries[2].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            headers.headerNames shouldBe listOf("Host", "Accept")
            body.isPresent shouldBe false
        }
    }

    @Test
    fun canParseRequestWithFileBody() {
        val parser = ReqInEditParser(object : FileReader {
            override fun read(path: String?): ByteArray {
                if (path == "./simple/body.json") return """
            {
              "hello": true
            }""".trimIndent().toByteArray()
                else throw FileNotFoundException(path)
            }
        })

        val fileLines = listOf(
                "POST /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Content-Type: application/json",
                "",
                "< ./simple/body.json",
                "",
                "###",
                ""
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 1

        entries[0].request.run {
            method shouldBe "POST"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Content-Type", "Content-Length")
            body.isPresent shouldBe true
            body.get().decodeBodyToString(Charsets.UTF_8) shouldBe
                    "{\n  \"hello\": true\n}"
        }
    }

    @Test
    fun canParseRequestWithFileMixedBody() {
        val parser = ReqInEditParser(object : FileReader {
            override fun read(path: String?): ByteArray {
                if (path == "./entries.json") return """  "file": true,""".toByteArray()
                else throw FileNotFoundException(path)
            }
        })

        val fileLines = listOf(
                "POST /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Content-Type: application/json",
                "",
                "",
                "{",
                "< ./entries.json",
                "  \"extra\": \"entry\"",
                "}",
                "   "
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 1

        entries[0].request.run {
            method shouldBe "POST"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Content-Type", "Content-Length")
            body.isPresent shouldBe true
            body.get().decodeBodyToString(Charsets.UTF_8) shouldBe
                    """{
                        |  "file": true,
                        |  "extra": "entry"
                        |}""".trimMargin()
        }
    }
}