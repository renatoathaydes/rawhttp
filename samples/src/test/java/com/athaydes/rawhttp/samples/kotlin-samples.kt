package com.athaydes.rawhttp.samples

import com.athaydes.rawhttp.core.RawHttp
import com.athaydes.rawhttp.core.client.TcpRawHttpClient
import io.kotlintest.Spec
import io.kotlintest.specs.StringSpec
import org.hamcrest.CoreMatchers.equalTo
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import spark.Spark
import java.net.Socket
import java.net.URI
import kotlin.text.Charsets.UTF_8

fun sparkServerInterceptor(spec: Spec, runTest: () -> Unit) {
    Spark.port(8082)
    Spark.get("/hello", "text/plain") { req, res -> "Hello" }
    Spark.post("/repeat", "text/plain") { req, res ->
        val count = req.queryParamOrDefault("count", "10").toInt()
        val text = req.queryParamOrDefault("text", "x")
        text.repeat(count)
    }
    Thread.sleep(150L)
    runTest()
    Spark.stop()
}

class KotlinSamples : StringSpec() {

    override val specInterceptors: List<(Spec, () -> Unit) -> Unit>
        get() = listOf(::sparkServerInterceptor)

    init {
        "Simple RawHTTP Kotlin Sample" {
            val req = RawHttp().parseRequest("GET /hello\r\nHost: localhost:8082").eagerly().run {
                assertThat(method, equalTo("GET"))
                assertThat(uri, equalTo(URI.create("http://localhost:8082/hello")))
                assertThat(headers["Host"], equalTo(listOf("localhost")))
                this
            }

            // send request to the Spark server
            val socket = Socket("localhost", 8082)
            req.writeTo(socket.getOutputStream())

            // check the response
            RawHttp().parseResponse(socket.getInputStream()).run {
                assertThat(statusCode, equalTo(200))
                assertTrue(body.isPresent)
                if (body.get().isChunked) {
                    assertThat(body.get().asChunkedBodyContents().get().asString(UTF_8), equalTo("Hello"))
                } else {
                    assertThat(body.get().asString(UTF_8), equalTo("Hello"))
                }
            }
        }

        "Post something using the TCP client" {
            val client = TcpRawHttpClient()

            val req = RawHttp().parseRequest("""
                POST /repeat?text=helloKotlin&count=1000
                User-Agent: RawHTTP
                Host: localhost:8082""".trimIndent())

            val res = client.send(req)

            res.run {
                assertThat(statusCode, equalTo(200))
                assertTrue(body.isPresent)

                val expectedBody = "helloKotlin".repeat(1_000)

                if (body.get().isChunked) {
                    assertThat(body.get().asChunkedBodyContents().get().asString(UTF_8), equalTo(expectedBody))
                } else {
                    assertThat(body.get().asString(UTF_8), equalTo(expectedBody))
                }
            }

        }
    }

}