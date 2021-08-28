package rawhttp.samples

import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.client.TcpRawHttpClient
import spark.Spark
import java.net.Socket
import java.net.URI
import java.time.Duration
import kotlin.text.Charsets.UTF_8

const val testPort = 8082

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

class KotlinSamples {

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            startSparkServer()
            RawHttp.waitForPortToBeTaken(testPort, Duration.ofSeconds(4))
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            stopSparkServer()
        }
    }

    @Test
    fun simpleRawHTTPKotlinSample() {
        val req = RawHttp().parseRequest("GET /hello\r\nHost: localhost:$testPort").eagerly().apply {
            method shouldBe "GET"
            uri shouldBe URI.create("http://localhost:$testPort/hello")
            headers["Host"] shouldBe listOf("localhost:$testPort")
        }

        // send request to the Spark server
        val socket = Socket("localhost", testPort)
        req.writeTo(socket.getOutputStream())

        // check the response
        RawHttp().parseResponse(socket.getInputStream()).run {
            statusCode shouldBe 200
            body shouldBePresent { b ->
                if (b.isChunked) {
                    b.asChunkedBodyContents().get().asString(UTF_8) shouldBe "Hello"
                } else {
                    b.asRawString(UTF_8) shouldBe "Hello"
                }
            }
        }
    }

    @Test
    fun postSomethingUsingTheTCPClient() {
        val client = TcpRawHttpClient()

        val req = RawHttp().parseRequest(
            """
                POST /repeat?text=helloKotlin&count=1000
                User-Agent: RawHTTP
                Host: localhost:$testPort""".trimIndent()
        )

        val res = client.send(req)

        res.run {
            statusCode shouldBe 200
            body shouldBePresent { b ->
                val expectedBody = "helloKotlin".repeat(1_000)
                if (b.isChunked) {
                    b.asChunkedBodyContents().get().asString(UTF_8) shouldBe expectedBody
                } else {
                    b.asRawString(UTF_8) shouldBe expectedBody
                }
            }
        }
    }

}
