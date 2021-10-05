package com.athaydes.rawhttp.reqinedit.js

import com.athaydes.rawhttp.reqinedit.HttpTestResult
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter
import com.athaydes.rawhttp.reqinedit.ReqInEditParserTest
import com.athaydes.rawhttp.reqinedit.ScriptException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import java.io.File

class JsEnvironmentTest {
    companion object {
        val jsEnv = JsEnvironment()
    }

    @Test
    fun canComputeRandomInt() {
        jsEnv.renderTemplate("number is {{ \$randomInt }}") shouldMatch ("number is \\d+")
    }

    @Test
    fun canComputeRandomUUID() {
        jsEnv.renderTemplate("rand UUID {{ \$uuid }}") shouldMatch ("rand UUID [a-f0-9-]{36}")
    }

    @Test
    fun canComputeTimestamp() {
        val now = System.currentTimeMillis()
        jsEnv.renderTemplate("{{\$timestamp}}").toLong() shouldBeInRange (now..now + 5000L)
    }

    @Test
    fun clientGlobalHasExpectedApi() {
        jsEnv.eval("client.global.isEmpty()").asBoolean() shouldBe true
        jsEnv.eval("client.global.set(\"name\", \"Michael\");")
        jsEnv.eval("client.global.set(\"other\", \"Something\");")
        jsEnv.eval("client.global.isEmpty()").asBoolean() shouldBe false
        jsEnv.renderTemplate("welcome {{name}}!") shouldBe "welcome Michael!"
        jsEnv.eval("client.global.get(\"other\")").asString() shouldBe "Something"
        jsEnv.eval("client.global.clear(\"other\")")
        jsEnv.eval("client.global.get(\"other\")").isNull shouldBe true
        jsEnv.eval("client.global.get(\"name\")").asString() shouldBe "Michael"
        jsEnv.eval("client.global.clearAll()")
        jsEnv.eval("client.global.get(\"name\")").isNull shouldBe true
        jsEnv.eval("client.global.get(\"other\")").isNull shouldBe true
        jsEnv.eval("client.global.isEmpty()").asBoolean() shouldBe true
    }

    @Test
    fun clientObjectHasExpectedFunctions() {
        jsEnv.eval("client.assert(true);")
        jsEnv.eval("client.assert(1);")
        shouldThrow<ScriptException> {
            jsEnv.eval("client.assert(false);")
        }.cause?.message shouldBe "assertion failed"
        shouldThrow<ScriptException> {
            jsEnv.eval("client.assert(1 < 0, \"1 is not greater than 0\");")
        }.cause?.message shouldBe "1 is not greater than 0"

        // declaring tests should not cause them to run
        jsEnv.eval(
            "client.test(\"js test\", function() {client.assert(1 + 1 === 2)});\n"
                    + "client.test(\"js second test\", function() {/*nothing*/});\n"
                    + "client.test(\"fails always\", function() {client.assert(false, \"no good\")});\n"
                    + "client.test(\"fails too\", function() {client.assert(false, \"no chance\")});\n"
        )

        // running tests should cause all failures to be reported
        val results = mutableListOf<HttpTestResult>()
        val reporter = HttpTestsReporter { result ->
            results.add(result)
        }

        jsEnv.runAllTests(reporter)

        results.size shouldBe 4

        results[0].name shouldBe "js test"
        results[0].isSuccess shouldBe true

        results[1].name shouldBe "js second test"
        results[1].isSuccess shouldBe true

        results[2].name shouldBe "fails always"
        results[2].isSuccess shouldBe false
        results[2].error shouldBe "no good"

        results[3].name shouldBe "fails too"
        results[3].isSuccess shouldBe false
        results[3].error shouldBe "no chance"
    }

    @Test
    fun responseObjectHasExpectedApiWithNonJson() {
        val response = RawHttp().parseResponse(
            "HTTP/1.1 404 Not Found\n" +
                    "Content-Type: text/plain; charset=US-ASCII\n" +
                    "Content-Length: 15\n" +
                    "Set-Cookie: cookie1=val1\n" +
                    "Set-Cookie: cookie2=val2\n" +
                    "Server: RawHTTP\n" +
                    "\n" +
                    "Hello ReqInEdit"
        ).eagerly()

        jsEnv.setResponse(response)

        jsEnv.eval("response.status").asInt() shouldBe 404
        jsEnv.eval("response.contentType.get('mimeType')").asString() shouldBe "text/plain"
        jsEnv.eval("response.contentType.get('charset')").asString() shouldBe "US-ASCII"
        jsEnv.eval("response.headers.valueOf('Content-Type')").asString() shouldBe "text/plain; charset=US-ASCII"
        jsEnv.eval("response.headers.valueOf('Content-Length')").asString() shouldBe "15"
        jsEnv.eval("response.headers.valueOf('Set-Cookie')").asString() shouldBe "cookie1=val1"
        val cookies = jsEnv.eval("response.headers.valuesOf('Set-Cookie')")
        cookies.isHostObject shouldBe true
        val cookiesObject = cookies.asHostObject<List<*>>()
        cookiesObject shouldBe listOf("cookie1=val1", "cookie2=val2")
        jsEnv.eval("response.headers.valueOf('Server')").asString() shouldBe "RawHTTP"
        jsEnv.eval("response.body").asString() shouldBe "Hello ReqInEdit"
    }

    @Test
    fun responseObjectHasExpectedApiWithJsonBody() {
        val response = RawHttp().parseResponse(
            "HTTP/1.1 200 OK\n" +
                    "Content-Type: application/json\n" +
                    "Content-Length: 28\n" +
                    "\n" +
                    "{\"foo\": \"bar\", \"cool\": true}"
        ).eagerly()

        jsEnv.setResponse(response)

        jsEnv.eval("response.status").asInt() shouldBe 200
        jsEnv.eval("response.contentType.get('mimeType')").asString() shouldBe "application/json"
        jsEnv.eval("response.contentType.get('charset')").isNull shouldBe true
        jsEnv.eval("response.headers.valueOf('Content-Type')").asString() shouldBe "application/json"
        jsEnv.eval("response.headers.valueOf('Content-Length')").asString() shouldBe "28"
        jsEnv.eval("typeof response.body").asString() shouldBe "object"
        jsEnv.eval("Object.keys(response.body).length").asInt() shouldBe 2
        jsEnv.eval("response.body.foo").asString() shouldBe "bar"
        jsEnv.eval("response.body.cool").asBoolean() shouldBe true
    }

    @Test
    fun canLoadRealJsEnvironment() {
        val httpFile = ReqInEditParserTest::class.java.getResource("http/get.http").file
        val prodEnv = JsEnvironment.loadEnvironment(File(httpFile), "prod")
        prodEnv.renderTemplate("{{ host }}") shouldBe "myserver.com"
        prodEnv.renderTemplate("{{ secret }}") shouldBe "123456"

        val testEnv = JsEnvironment.loadEnvironment(File(httpFile), "test")
        testEnv.renderTemplate("{{ host }}") shouldBe "localhost:8080"
        testEnv.renderTemplate("{{ secret }}") shouldBe "password"
    }

}