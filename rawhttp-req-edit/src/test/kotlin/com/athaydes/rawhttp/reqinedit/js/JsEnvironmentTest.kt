package com.athaydes.rawhttp.reqinedit.js

import io.kotlintest.matchers.between
import io.kotlintest.matchers.match
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test
import javax.script.ScriptException

class JsEnvironmentTest {
    companion object {
        val jsEnv = JsEnvironment()
    }

    @Test
    fun canComputeRandomInt() {
        jsEnv.apply("number is {{ \$randomInt }}") should match("number is \\d+")
    }

    @Test
    fun canComputeRandomUUID() {
        jsEnv.apply("rand UUID {{ \$uuid }}") should match("rand UUID [a-f0-9-]{36}")
    }

    @Test
    fun canComputeTimestamp() {
        val now = System.currentTimeMillis()
        jsEnv.apply("{{\$timestamp}}").toLong() shouldBe between(now, now + 5000L)
    }

    @Test
    fun clientGlobalHasExpectedApi() {
        jsEnv.eval("client.global.isEmpty()") shouldBe true
        jsEnv.eval("client.global.set(\"name\", \"Michael\");")
        jsEnv.eval("client.global.set(\"other\", \"Something\");")
        jsEnv.eval("client.global.isEmpty()") shouldBe false
        jsEnv.apply("welcome {{name}}!") shouldBe "welcome Michael!"
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
        jsEnv.runAllTests() shouldBe listOf(
                "Test failed: fails always (no good)",
                "Test failed: fails too (no chance)")
    }
}