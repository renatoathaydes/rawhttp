package com.athaydes.rawhttp.reqinedit

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import org.junit.Test
import java.io.FileNotFoundException
import java.nio.file.Path

class ReqInEditParserTest {

    @Test
    fun canParseSimplestRequest() {
        val parser = ReqInEditParser()

        val fileLines = listOf("http://example.org")

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 1

        entries[0].run {
            request shouldBe """
                GET http://example.org
            """.trimIndent()
            requestBody should beEmpty()
            script.isPresent shouldBe false
            responseRef.isPresent shouldBe false
        }
    }

    @Test
    fun canParseSimpleRequests() {
        val parser = ReqInEditParser()

        val fileLines = listOf(
                "### here start my requests",
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
                "Accept: application/json",
                "",
                "### done",
                ""
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 3

        entries[0].run {
            request shouldBe """
                GET /something HTTP/1.1
                Host: example.org
                Accept: text/html
                
            """.trimIndent()
            requestBody should beEmpty()
            responseRef.isPresent shouldBe false
            script.isPresent shouldBe false
        }

        entries[1].run {
            request shouldBe """
                POST /resource/some-id HTTP/1.1
                Host: example.org
                Content-Type: application/json
                
            """.trimIndent()
            requestBody shouldBe listOf(StringOrFile.ofString("{\"example\": \"value\", \"count\": 1}"))
            responseRef.isPresent shouldBe false
            script.isPresent shouldBe false
        }

        entries[2].run {
            request shouldBe """
                GET /resource/some-id HTTP/1.1
                Host: example.org
                Accept: application/json
                
            """.trimIndent()
            requestBody should beEmpty()
            responseRef.isPresent shouldBe false
            script.isPresent shouldBe false
        }
    }

    @Test
    fun canParseRequestsWithResponseRef() {
        val parser = ReqInEditParser()

        val fileLines = listOf("http://example.org",
                "",
                "<> first-response",
                "",
                "###",
                "# another request",
                "http://another.com",
                "",
                "<> second-response",
                ""
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 2

        entries[0].run {
            request shouldBe """
                GET http://example.org
                
            """.trimIndent()
            requestBody should beEmpty()
            script.isPresent shouldBe false
            responseRef.isPresent shouldBe true
            responseRef.get() shouldBe "first-response"
        }

        entries[1].run {
            request shouldBe """
                GET http://another.com
                
            """.trimIndent()
            requestBody should beEmpty()
            script.isPresent shouldBe false
            responseRef.isPresent shouldBe true
            responseRef.get() shouldBe "second-response"
        }
    }

    @Test
    fun canParseRequestWithFileBody() {
        object : FileReader {
            override fun read(path: Path?): ByteArray {
                if (path?.toString() == "./simple/body.json") return """
            {
              "hello": true
            }""".trimIndent().toByteArray()
                else throw FileNotFoundException(path?.toString() ?: "null")
            }
        }

        val parser = ReqInEditParser()

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

        entries[0].run {
            request shouldBe """
                POST /resource/some-id HTTP/1.1
                Host: example.org
                Content-Type: application/json
                
            """.trimIndent()
            requestBody shouldBe listOf(StringOrFile.ofFile("./simple/body.json"))
            script.isPresent shouldBe false
            responseRef.isPresent shouldBe false
        }
    }

    @Test
    fun canParseRequestWithFileMixedBody() {
        val parser = ReqInEditParser()

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

        entries[0].run {
            request shouldBe """
                POST /resource/some-id HTTP/1.1
                Host: example.org
                Content-Type: application/json
                
            """.trimIndent()
            requestBody shouldBe listOf(StringOrFile.ofString("{"),
                    StringOrFile.ofFile("./entries.json"),
                    StringOrFile.ofString("  \"extra\": \"entry\""),
                    StringOrFile.ofString("}"))
            script.isPresent shouldBe false
            responseRef.isPresent shouldBe false
        }
    }

    @Test
    fun canParseRequestWithEmbeddedResponseHandler() {
        val parser = ReqInEditParser()

        val fileLines = listOf(
                "GET /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Accept: application/json",
                "",
                "> {% ",
                "    client.test(\"Request executed successfully\", function() {",
                "        client.assert(response.status === 200, \"Response status is not 200\");",
                "    });",
                " %} ",
                ""
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 1

        entries[0].run {
            request shouldBe """
                GET /resource/some-id HTTP/1.1
                Host: example.org
                Accept: application/json

            """.trimIndent()
            script.isPresent shouldBe true
            script.get() shouldBe StringOrFile.ofString("\n    client.test(\"Request executed successfully\", function() {\n" +
                    "        client.assert(response.status === 200, \"Response status is not 200\");\n" +
                    "    });\n ")
            responseRef.isPresent shouldBe false
        }
    }

    @Test
    fun canParseRequestWithIncludedFileResponseHandler() {
        val parser = ReqInEditParser()

        val fileLines = listOf(
                "GET /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Accept: application/json",
                "",
                "> my_response_handler.js",
                ""
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 1

        entries[0].run {
            request shouldBe """
                GET /resource/some-id HTTP/1.1
                Host: example.org
                Accept: application/json

            """.trimIndent()
            script.isPresent shouldBe true
            script.get() shouldBe StringOrFile.ofFile("my_response_handler.js")
            responseRef.isPresent shouldBe false
        }
    }

}