package com.athaydes.rawhttp.reqinedit

import io.kotlintest.matchers.shouldBe
import org.junit.Test
import java.io.File
import java.io.FileNotFoundException
import java.net.URI

class ReqInEditParserTest {

    @Test
    fun canParseSimplestRequest() {
        val parser = ReqInEditParser()

        val fileLines = listOf("http://example.org")

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 1

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org")
            headers.headerNames shouldBe listOf("Host")
            headers["Host"] shouldBe listOf("example.org")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe false
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

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org/something")
            headers.headerNames shouldBe listOf("Host", "Accept")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe false

        entries[1].request.run {
            method shouldBe "POST"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Content-Type", "Content-Length")
            body.isPresent shouldBe true
            body.get().decodeBodyToString(Charsets.UTF_8) shouldBe
                    "{\"example\": \"value\", \"count\": 1}"
        }
        entries[1].script.isPresent shouldBe false

        entries[2].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            headers.headerNames shouldBe listOf("Host", "Accept")
            body.isPresent shouldBe false
        }
        entries[2].script.isPresent shouldBe false
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

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org")
            headers.headerNames shouldBe listOf("Host")
            headers["Host"] shouldBe listOf("example.org")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe false
        entries[0].responseRef.isPresent shouldBe true
        entries[0].responseRef.get() shouldBe "first-response"

        entries[1].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://another.com")
            headers.headerNames shouldBe listOf("Host")
            headers["Host"] shouldBe listOf("another.com")
            body.isPresent shouldBe false
        }
        entries[1].script.isPresent shouldBe false
        entries[1].responseRef.isPresent shouldBe true
        entries[1].responseRef.get() shouldBe "second-response"
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
        entries[0].script.isPresent shouldBe false
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
        entries[0].script.isPresent shouldBe false
    }

    @Test
    fun canParseRequestWithEnvVarsMocked() {
        val parser = ReqInEditParser()

        val fileLines = listOf("http://{{host}}",
                "Accept: {{ contentType }}",
                "User-Agent: RawHTTP")

        val httpEnv = HttpEnvironment { line ->
            line.replace("{{host}}", "example.org")
                    .replace("{{ contentType }}", "application/json")
        }

        val entries = parser.parse(fileLines.stream(), httpEnv)

        entries.size shouldBe 1

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org")
            headers.headerNames shouldBe listOf("Accept", "User-Agent", "Host")
            headers["Host"] shouldBe listOf("example.org")
            headers["Accept"] shouldBe listOf("application/json")
            headers["User-Agent"] shouldBe listOf("RawHTTP")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe false
    }

    @Test
    fun canLoadRealJsEnvironment() {
        val httpFile = ReqInEditParserTest::class.java.getResource("http/get.http").file
        val prodEnv = ReqInEditParser.loadEnvironment(File(httpFile), "prod")
        prodEnv.apply("{{ host }}") shouldBe "myserver.com"
        prodEnv.apply("{{ secret }}") shouldBe "123456"

        val testEnv = ReqInEditParser.loadEnvironment(File(httpFile), "test")
        testEnv.apply("{{ host }}") shouldBe "localhost:8080"
        testEnv.apply("{{ secret }}") shouldBe "password"
    }

    @Test
    fun canParseRequestWithRealEnvironments() {
        val httpFile = ReqInEditParserTest::class.java.getResource("http/get.http").file
        val parser = ReqInEditParser()

        val entries = parser.parse(File(httpFile), "prod")

        entries.size shouldBe 1

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://myserver.com")
            headers.headerNames shouldBe listOf("Accept", "Authorize", "Host")
            headers["Host"] shouldBe listOf("myserver.com")
            headers["Accept"] shouldBe listOf("*/*")
            headers["Authorize"] shouldBe listOf("Bearer 123456")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe false

        val entriesTest = parser.parse(File(httpFile), "test")

        entriesTest.size shouldBe 1

        entriesTest[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://localhost:8080")
            headers.headerNames shouldBe listOf("Accept", "Authorize", "Host")
            headers["Host"] shouldBe listOf("localhost")
            headers["Accept"] shouldBe listOf("*/*")
            headers["Authorize"] shouldBe listOf("Bearer password")
            body.isPresent shouldBe false
        }
        entriesTest[0].script.isPresent shouldBe false
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

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Accept")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe true
        entries[0].script.get() shouldBe "\n    client.test(\"Request executed successfully\", function() {\n" +
                "        client.assert(response.status === 200, \"Response status is not 200\");\n" +
                "    });\n "
    }

    @Test
    fun canParseRequestWithIncludedResponseHandler() {
        val parser = ReqInEditParser()
        val includedJs1 = ReqInEditParserTest::class.java.getResource("response_handler.js").file
        val includedJs2 = ReqInEditParserTest::class.java.getResource("response_handler_2.js").file
        val fileLines = listOf(
                "GET /resource/some-id HTTP/1.1",
                "Host: example.org",
                "Accept: application/json",
                "",
                "> $includedJs1",
                "###",
                "POST http://example.com/foo",
                "",
                "foo bar",
                "> $includedJs2",
                ""
        )

        val entries = parser.parse(fileLines.stream())

        entries.size shouldBe 2

        entries[0].request.run {
            method shouldBe "GET"
            uri shouldBe URI.create("http://example.org/resource/some-id")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Accept")
            body.isPresent shouldBe false
        }
        entries[0].script.isPresent shouldBe true
        entries[0].script.get() shouldBe "client.global.set(\"auth\", response.body.token);"

        entries[1].request.run {
            method shouldBe "POST"
            uri shouldBe URI.create("http://example.com/foo")
            // the parser must add Content-Length as the message contains a body
            headers.headerNames shouldBe listOf("Host", "Content-Length")
            body.isPresent shouldBe true
            body.get().decodeBodyToString(Charsets.UTF_8) shouldBe "foo bar"
        }
        entries[1].script.isPresent shouldBe true
        entries[1].script.get() shouldBe "client.test(\"check\", function() {\n" +
                "    client.assert(response.body == \"foo bar\");\n" +
                "});"
    }

}