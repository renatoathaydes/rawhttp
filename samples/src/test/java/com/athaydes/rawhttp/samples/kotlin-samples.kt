package com.athaydes.rawhttp.samples

import com.athaydes.rawhttp.core.RawHttp
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
                assertThat(headers["Host"], equalTo(listOf("localhost:8082")))
                this
            }

            // send request to the Spark server
            val socket = Socket("localhost", 8082)
            req.writeTo(socket.getOutputStream())

            // check the response
            RawHttp().parseResponse(socket.getInputStream()).run {
                assertThat(statusCode, equalTo(200))
                assertTrue(body.isPresent)
                assertThat(body.get().eager().asString(UTF_8), equalTo("Hello"))
            }
        }
    }

}