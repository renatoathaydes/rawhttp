package com.athaydes.rawhttp.reqinedit

import io.kotlintest.matchers.shouldBe
import org.junit.Test
import java.net.URI

class ReqInEditParserTest {

    @Test
    fun canParseSimpleRequest() {
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
}