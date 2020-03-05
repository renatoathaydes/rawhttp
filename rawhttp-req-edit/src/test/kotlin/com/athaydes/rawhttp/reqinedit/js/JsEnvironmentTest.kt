package com.athaydes.rawhttp.reqinedit.js

import com.athaydes.rawhttp.reqinedit.HttpTestResult
import com.athaydes.rawhttp.reqinedit.HttpTestsReporter
import io.kotlintest.matchers.between
import io.kotlintest.matchers.match
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test
import rawhttp.core.RawHttp
import javax.script.ScriptException

class JsEnvironmentTest {
    companion object {
        val jsEnv = JsEnvironment()
    }

    @Test
    fun canComputeRandomInt() {
        jsEnv.renderTemplate("number is {{ \$randomInt }}") should match("number is \\d+")
    }

    @Test
    fun canComputeRandomUUID() {
        jsEnv.renderTemplate("rand UUID {{ \$uuid }}") should match("rand UUID [a-f0-9-]{36}")
    }

    @Test
    fun canComputeTimestamp() {
        val now = System.currentTimeMillis()
        jsEnv.renderTemplate("{{\$timestamp}}").toLong() shouldBe between(now, now + 5000L)
    }

    @Test
    fun clientGlobalHasExpectedApi() {
        jsEnv.eval("client.global.isEmpty()") shouldBe true
        jsEnv.eval("client.global.set(\"name\", \"Michael\");")
        jsEnv.eval("client.global.set(\"other\", \"Something\");")
        jsEnv.eval("client.global.isEmpty()") shouldBe false
        jsEnv.renderTemplate("welcome {{name}}!") shouldBe "welcome Michael!"
        jsEnv.eval("client.global.get(\"other\")") shouldBe "Something"
        jsEnv.eval("client.global.clear(\"other\")")
        jsEnv.eval("client.global.get(\"other\")") shouldBe null
        jsEnv.eval("client.global.get(\"name\")") shouldBe "Michael"
        jsEnv.eval("client.global.clearAll()")
        jsEnv.eval("client.global.get(\"name\")") shouldBe null
        jsEnv.eval("client.global.get(\"other\")") shouldBe null
        jsEnv.eval("client.global.isEmpty()") shouldBe true
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
        jsEnv.eval("client.test(\"js test\", function() {client.assert(1 + 1 === 2)});\n"
                + "client.test(\"js second test\", function() {/*nothing*/});\n"
                + "client.test(\"fails always\", function() {client.assert(false, \"no good\")});\n"
                + "client.test(\"fails too\", function() {client.assert(false, \"no chance\")});\n")

        // running tests should cause all failures to be reported
        val results = mutableListOf<HttpTestResult>()
        val reporter = HttpTestsReporter { result ->
            results.add(result)
        }

        jsEnv.runAllTests(reporter)

        results.size shouldBe 2

        results[0].name shouldBe "fails always"
        results[0].isSuccess shouldBe false
        results[0].error shouldBe "foo"

        results[1].name shouldBe "fails too"
        results[1].isSuccess shouldBe false
        results[1].error shouldBe "foo"
    }

    @Test
    fun responseObjectHasExpectedApiWithNonJson() {
        val response = RawHttp().parseResponse("HTTP/1.1 404 Not Found\n" +
                "Content-Type: text/plain; charset=US-ASCII\n" +
                "Content-Length: 15\n" +
                "Set-Cookie: cookie1=val1\n" +
                "Set-Cookie: cookie2=val2\n" +
                "Server: RawHTTP\n" +
                "\n" +
                "Hello ReqInEdit").eagerly()

        jsEnv.setResponse(response)

        jsEnv.eval("response.status") shouldBe 404
        jsEnv.eval("response.contentType.mimeType") shouldBe "text/plain"
        jsEnv.eval("response.contentType.charset") shouldBe "US-ASCII"
        jsEnv.eval("response.headers.valueOf('Content-Type')") shouldBe "text/plain; charset=US-ASCII"
        jsEnv.eval("response.headers.valueOf('Content-Length')") shouldBe "15"
        jsEnv.eval("response.headers.valueOf('Set-Cookie')") shouldBe "cookie1=val1"
        jsEnv.eval("response.headers.valuesOf('Set-Cookie')") shouldBe listOf("cookie1=val1", "cookie2=val2")
        jsEnv.eval("response.headers.valueOf('Server')") shouldBe "RawHTTP"
        jsEnv.eval("response.body") shouldBe "Hello ReqInEdit"
    }

    @Test
    fun responseObjectHasExpectedApiWithJsonBody() {
        val response = RawHttp().parseResponse("HTTP/1.1 200 OK\n" +
                "Content-Type: application/json\n" +
                "Content-Length: 28\n" +
                "\n" +
                "{\"foo\": \"bar\", \"cool\": true}").eagerly()

        jsEnv.setResponse(response)

        jsEnv.eval("response.status") shouldBe 200
        jsEnv.eval("response.contentType.mimeType") shouldBe "application/json"
        jsEnv.eval("response.contentType.charset") shouldBe null
        jsEnv.eval("response.headers.valueOf('Content-Type')") shouldBe "application/json"
        jsEnv.eval("response.headers.valueOf('Content-Length')") shouldBe "28"
        jsEnv.eval("typeof response.body") shouldBe "object"
        jsEnv.eval("Object.keys(response.body).length") shouldBe 2
        jsEnv.eval("response.body.foo") shouldBe "bar"
        jsEnv.eval("response.body.cool") shouldBe true
    }
}