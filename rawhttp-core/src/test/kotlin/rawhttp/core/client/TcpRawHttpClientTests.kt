package rawhttp.core.client

import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import spark.Spark.get
import spark.Spark.port
import spark.Spark.post
import spark.Spark.stop
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

fun startSparkServerFor(spec: String) {
    println("Starting Spark for spec: $spec")
    port(8083)
    get("/say-hi", "text/plain") { _, _ -> "Hi there" }
    get("/say-hi", "application/json") { _, _ -> "{ \"message\": \"Hi there\" }" }
    post("/echo", "text/plain") { req, _ -> req.body() }
    post("/continue", "text/plain") { _, _ -> "continuing" }

    waitForPortToBeTaken(8083, Duration.ofSeconds(2))
    println("Spark is on")
}

fun stopSparkServer() {
    println("Stopping Spark")
    stop()
    Thread.sleep(150)
}

class TcpRawHttp10ClientTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeSpec() {
            startSparkServerFor("TcpRawHttp10ClientTest")
        }

        @AfterAll
        @JvmStatic
        fun afterSpec() {
            stopSparkServer()
        }
    }

    @Test
    fun `Must be able to perform a simple HTTP 1_0 request against a real HTTP server`() {
        TcpRawHttpClient().send(
            RawHttp().parseRequest(
                "GET http://localhost:8083/say-hi HTTP/1.0"
            )
        ).eagerly().run {
            statusCode shouldBe 200
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBeIn setOf("Hi there", "{ \"message\": \"Hi there\" }")
            }
        }
    }

    @Test
    fun `Must be able to perform a HTTP 1_0 GET request with headers against a real HTTP server`() {
        TcpRawHttpClient().send(
            RawHttp().parseRequest(
                "GET /say-hi HTTP/1.0\r\n" +
                        "Host: localhost:8083\r\n" +
                        "Accept: text/plain"
            )
        ).eagerly().run {
            statusCode shouldBe 200
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBe "Hi there"
            }
        }
    }

    @Test
    fun `Must be able to perform a HTTP 1_0 POST request with headers and a body against a real HTTP server`() {
        TcpRawHttpClient().send(
            RawHttp().parseRequest(
                "POST /echo HTTP/1.0\r\n" +
                        "Host: localhost:8083\r\n" +
                        "Accept: text/plain\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: 11\r\n" +
                        "\r\n" +
                        "hello world"
            )
        ).eagerly().run {
            statusCode shouldBe 200
            body shouldBePresent {
                it.asRawString(UTF_8) shouldBe "hello world"
            }
        }
    }

}

class TcpRawHttp11ClientTest {

    companion object {
        @BeforeAll
        @JvmStatic
        fun beforeSpec() {
            startSparkServerFor("TcpRawHttp11ClientTest")
        }

        @AfterAll
        @JvmStatic
        fun afterSpec() {
            stopSparkServer()
        }
    }

    @Test
    fun `Must be able to perform a simple HTTP 1_1 request against a real HTTP server`() {
        TcpRawHttpClient().send(
            RawHttp().parseRequest(
                "GET http://localhost:8083/say-hi HTTP/1.1"
            )
        ).eagerly().run {
            statusCode shouldBe 200
            body shouldBePresent {
                it.decodeBodyToString(UTF_8) shouldBeIn setOf("Hi there", "{ \"message\": \"Hi there\" }")
            }
        }
    }

    @Test
    fun `Must be able to perform a HTTP 1_1 request with headers against a real HTTP server`() {
        TcpRawHttpClient().send(
            RawHttp().parseRequest(
                "GET /say-hi HTTP/1.1\r\n" +
                        "Host: localhost:8083\r\n" +
                        "Accept: text/plain"
            )
        ).eagerly().run {
            statusCode shouldBe 200
            body shouldBePresent {
                it.decodeBodyToString(UTF_8) shouldBe "Hi there"
            }
        }
    }

    @Test
    fun `Must be able to perform a HTTP 1_1 request with headers and a body against a real HTTP server`() {
        TcpRawHttpClient().send(
            RawHttp().parseRequest(
                "POST /continue HTTP/1.1\r\n" +
                        "Host: localhost:8083\r\n" +
                        "Accept: text/plain\r\n" +
                        "Content-Type: text/plain\r\n" +
                        "Content-Length: 11\r\n" +
                        "Expect: 100-continue\r\n" +
                        "\r\n" +
                        "hello world"
            )
        ).eagerly().run {
            statusCode shouldBe 200
            body shouldBePresent {
                it.decodeBodyToString(UTF_8) shouldBe "continuing"
            }
        }
    }

}
