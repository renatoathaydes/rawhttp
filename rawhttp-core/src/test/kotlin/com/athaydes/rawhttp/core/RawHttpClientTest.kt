package com.athaydes.rawhttp.core

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import spark.Spark.get
import spark.Spark.port
import spark.Spark.stop
import java.net.Socket

class RawHttpClientTest : StringSpec() {

    override val specInterceptors: List<(Spec, () -> Unit) -> Unit>
        get() = listOf({ spec, runTest ->
            println("Starting Spark")
            port(8083)
            get("/say-hi") { req, res -> "Hi there" }

            println("Spark is on")
            Thread.sleep(150)
            runTest()

            println("Stopping Spark")
            stop()
        })

    init {
        "Must be able to perform a simple HTTP 1.0 request against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "GET http://localhost:8083/say-hi HTTP/1.0\r\n\r\r\n")).run {
                    String(bodyReader.asBytes()) shouldBe "Hi there"
                }
            }
        }
    }

}

