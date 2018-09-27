package rawhttp.samples

import org.hamcrest.CoreMatchers.equalTo
import org.junit.AfterClass
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.client.TcpRawHttpClient
import spark.Spark
import java.io.IOException
import java.net.Socket
import java.net.URI
import kotlin.text.Charsets.UTF_8

val testPort = 8082

fun startSparkServer() {
    Spark.port(testPort)
    Spark.get("/hello", "text/plain") { _, _ -> "Hello" }
    Spark.post("/repeat", "text/plain") { req, _ ->
        val count = req.queryParamOrDefault("count", "10").toInt()
        val text = req.queryParamOrDefault("text", "x")
        text.repeat(count)
    }
}

fun stopSparkServer() {
    Spark.stop()
}

fun waitForPortToBeTaken(port: Int) {
    for (it in 1..5) {
        try {
            val socket = Socket("localhost", port)
            socket.close()
            return
        } catch (e: IOException) {
            println("Port 8092 not taken yet, waiting...")
            Thread.sleep(100L)
        }
    }
    throw AssertionError("Port $port was not taken within the timeout")
}

class KotlinSamples {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            startSparkServer()
            waitForPortToBeTaken(testPort)
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            stopSparkServer()
        }
    }

    @Test
    fun simpleRawHTTPKotlinSample() {
        val req = RawHttp().parseRequest("GET /hello\r\nHost: localhost:$testPort").eagerly().run {
            assertThat(method, equalTo("GET"))
            assertThat(uri, equalTo(URI.create("http://localhost:$testPort/hello")))
            assertThat(headers["Host"], equalTo(listOf("localhost")))
            this
        }

        // send request to the Spark server
        val socket = Socket("localhost", testPort)
        req.writeTo(socket.getOutputStream())

        // check the response
        RawHttp().parseResponse(socket.getInputStream()).run {
            assertThat(statusCode, equalTo(200))
            assertTrue(body.isPresent)
            if (body.get().isChunked) {
                assertThat(body.get().asChunkedBodyContents().get().asString(UTF_8), equalTo("Hello"))
            } else {
                assertThat(body.get().asRawString(UTF_8), equalTo("Hello"))
            }
        }
    }

    @Test
    fun postSomethingUsingTheTCPClient() {
        val client = TcpRawHttpClient()

        val req = RawHttp().parseRequest("""
                POST /repeat?text=helloKotlin&count=1000
                User-Agent: RawHTTP
                Host: localhost:$testPort""".trimIndent())

        val res = client.send(req)

        res.run {
            assertThat(statusCode, equalTo(200))
            assertTrue(body.isPresent)

            val expectedBody = "helloKotlin".repeat(1_000)

            if (body.get().isChunked) {
                assertThat(body.get().asChunkedBodyContents().get().asString(UTF_8), equalTo(expectedBody))
            } else {
                assertThat(body.get().asRawString(UTF_8), equalTo(expectedBody))
            }
        }
    }

}