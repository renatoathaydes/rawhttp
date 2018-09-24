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
import java.lang.Thread.sleep
import java.net.Socket
import java.net.URI
import kotlin.text.Charsets.UTF_8

fun startSparkServer() {
    Spark.port(8082)
    Spark.get("/hello", "text/plain") { _, _ -> "Hello" }
    Spark.post("/repeat", "text/plain") { req, _ ->
        val count = req.queryParamOrDefault("count", "10").toInt()
        val text = req.queryParamOrDefault("text", "x")
        text.repeat(count)
    }
    sleep(150L)
}

fun stopSparkServer() {
    Spark.stop()
}

class KotlinSamples {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setup() {
            startSparkServer()
            sleep(250L)
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            stopSparkServer()
        }
    }

    @Test
    fun simpleRawHTTPKotlinSample() {
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
                Host: localhost:8082""".trimIndent())

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