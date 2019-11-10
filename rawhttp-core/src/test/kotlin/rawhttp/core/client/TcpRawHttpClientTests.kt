package rawhttp.core.client

import io.kotlintest.Spec
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.bePresent
import rawhttp.core.shouldBeOneOf
import spark.Spark.get
import spark.Spark.port
import spark.Spark.post
import spark.Spark.stop
import java.nio.charset.StandardCharsets.UTF_8
import java.time.Duration

fun sparkServerInterceptor(spec: Spec, runTest: () -> Unit) {
    println("Starting Spark for spec: $spec")
    port(8083)
    get("/say-hi", "text/plain") { _, _ -> "Hi there" }
    get("/say-hi", "application/json") { _, _ -> "{ \"message\": \"Hi there\" }" }
    post("/echo", "text/plain") { req, _ -> req.body() }
    post("/continue", "text/plain") { req, res -> "continuing" }

    waitForPortToBeTaken(8083, Duration.ofSeconds(2))
    println("Spark is on")
    runTest()

    println("Stopping Spark")
    stop()
    Thread.sleep(150)
}

class TcpRawHttp10ClientTest : StringSpec() {

    override val specInterceptors: List<(Spec, () -> Unit) -> Unit>
        get() = listOf(::sparkServerInterceptor)

    init {
        "Must be able to perform a simple HTTP 1.0 request against a real HTTP server" {
            TcpRawHttpClient().send(RawHttp().parseRequest(
                    "GET http://localhost:8083/say-hi HTTP/1.0")).eagerly().run {
                body should bePresent {
                    it.asRawString(UTF_8) shouldBeOneOf setOf("Hi there", "{ \"message\": \"Hi there\" }")
                }
            }
        }

        "Must be able to perform a HTTP 1.0 request with headers against a real HTTP server" {
            TcpRawHttpClient().send(RawHttp().parseRequest(
                    "GET /say-hi HTTP/1.0\r\n" +
                            "Host: localhost:8083\r\n" +
                            "Accept: text/plain")).eagerly().run {
                body should bePresent {
                    it.asRawString(UTF_8) shouldBe "Hi there"
                }
            }
        }

        "Must be able to perform a HTTP 1.0 request with headers and a body against a real HTTP server" {
            TcpRawHttpClient().send(RawHttp().parseRequest(
                    "POST /echo HTTP/1.0\r\n" +
                            "Host: localhost:8083\r\n" +
                            "Accept: text/plain\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 11\r\n" +
                            "\r\n" +
                            "hello world")).eagerly().run {
                body should bePresent {
                    it.asRawString(UTF_8) shouldBe "hello world"
                }
            }
        }
    }

}

class TcpRawHttp11ClientTest : StringSpec() {

    override val specInterceptors: List<(Spec, () -> Unit) -> Unit>
        get() = listOf(::sparkServerInterceptor)

    init {
        "Must be able to perform a simple HTTP 1.1 request against a real HTTP server" {
            TcpRawHttpClient().send(RawHttp().parseRequest(
                    "GET http://localhost:8083/say-hi HTTP/1.1")).eagerly().run {
                body should bePresent {
                    it.decodeBodyToString(UTF_8) shouldBeOneOf setOf("Hi there", "{ \"message\": \"Hi there\" }")
                }
            }

        }

        "Must be able to perform a HTTP 1.1 request with headers against a real HTTP server" {
            TcpRawHttpClient().send(RawHttp().parseRequest(
                    "GET /say-hi HTTP/1.1\r\n" +
                            "Host: localhost:8083\r\n" +
                            "Accept: text/plain")).eagerly().run {
                body should bePresent {
                    it.decodeBodyToString(UTF_8) shouldBe "Hi there"
                }
            }
        }

        "Must be able to perform a HTTP 1.1 request with headers and a body against a real HTTP server" {
            TcpRawHttpClient().send(RawHttp().parseRequest(
                    "POST /continue HTTP/1.1\r\n" +
                            "Host: localhost:8083\r\n" +
                            "Accept: text/plain\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 11\r\n" +
                            "Expect: 100-continue\r\n" +
                            "\r\n" +
                            "hello world")).eagerly().run {
                body should bePresent {
                    it.decodeBodyToString(UTF_8) shouldBe "continuing"
                }
            }
        }

    }

}