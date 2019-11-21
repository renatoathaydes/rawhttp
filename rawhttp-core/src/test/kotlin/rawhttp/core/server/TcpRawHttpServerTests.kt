package rawhttp.core.server

import io.kotlintest.Spec
import io.kotlintest.matchers.fail
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldHave
import io.kotlintest.specs.StringSpec
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.RequestLine
import rawhttp.core.bePresent
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.notBePresent
import rawhttp.core.validDateHeader
import java.lang.Thread.sleep
import java.net.InetAddress
import java.net.Socket
import java.net.SocketException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Optional

class TcpRawHttpServerTests : StringSpec() {

    companion object {
        private val http = RawHttp()
    }

    private val server = TcpRawHttpServer(8093)
    private val httpClient = TcpRawHttpClient()

    private object TestRouter : Router {
        override fun route(req: RawHttpRequest): Optional<RawHttpResponse<*>> {
            return Optional.ofNullable(when (req.uri.path) {
                "/hello", "/" ->
                    when (req.method) {
                        "GET" ->
                            http.parseResponse("HTTP/1.1 200 OK\n" +
                                    "Content-Type: text/plain"
                            ).withBody(StringBody("Hello RawHTTP!"))
                        "DELETE" ->
                            throw Exception("Cannot delete")
                        "POST" ->
                            http.parseResponse("HTTP/1.1 200 OK").withBody(StringBody("Thanks"))
                        else ->
                            http.parseResponse("HTTP/1.1 405 Method Not Allowed\n" +
                                    "Content-Type: text/plain"
                            ).withBody(StringBody("Sorry, can't handle this method"))
                    }
                "/throw" -> throw Exception("Not doing it!")
                "/null" -> null
                else ->
                    http.parseResponse("HTTP/1.1 404 Not Found\n" +
                            "Content-Type: text/plain"
                    ).withBody(StringBody("Content was not found"))
            })
        }

        override fun continueResponse(requestLine: RequestLine?, headers: RawHttpHeaders?): RawHttpResponse<Void> {
            return super.continueResponse(requestLine, headers).withHeaders(RawHttpHeaders.newBuilder()
                    .with("Accept-100", "True")
                    .build())
        }
    }

    private fun startServer() {
        server.start(TestRouter)
    }

    private fun cleanup() {
        server.stop()
        httpClient.close()
    }

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        startServer()
        waitForPortToBeTaken(8093, Duration.ofSeconds(2))
        try {
            spec()
        } finally {
            cleanup()
        }
    }

    init {
        "Server can handle successful http client request" {
            val request = http.parseRequest("GET http://localhost:8093/hello")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 200
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
            }
        }

        "Server can handle multiple successful http client requests" {
            val request = http.parseRequest("GET http://localhost:8093/hello")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 200
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
            }

            sleep(500)

            val response2 = httpClient.send(request).eagerly()

            response2.statusCode shouldBe 200
            response2.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
            }
        }

        "Server can handle wrong method http client request" {
            val request = http.parseRequest("PUT http://localhost:8093")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 405
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Sorry, can't handle this method"
            }
        }

        "Server can handle wrong path http client request" {
            val request = http.parseRequest("GET http://localhost:8093/wrong/path")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 404
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Content was not found"
            }
        }

        "Server returns default error response when router throws an Exception" {
            val request = http.parseRequest("POST http://localhost:8093/throw")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 500
            response.headers shouldHave validDateHeader()
            response.headers["Content-Type"] shouldBe listOf("text/plain")
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "A Server Error has occurred."
            }
        }

        "Server returns default error response when request handler throws an Exception" {
            val request = http.parseRequest("DELETE http://localhost:8093")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 500
            response.headers shouldHave validDateHeader()
            response.headers["Content-Type"] shouldBe listOf("text/plain")
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "A Server Error has occurred."
            }
        }

        "Server returns default error response when request handler returns null" {
            val request = http.parseRequest("Get http://localhost:8093/null")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 404
            response.headers shouldHave validDateHeader()
            response.headers["Content-Type"] shouldBe listOf("text/plain")
            response.body should bePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Resource was not found."
            }
        }

        "Server should persist connection on all HTTP/1.1 requests" {
            val socket = Socket("0.0.0.0", 8093)
            http.parseRequest("GET /hello HTTP/1.1\r\nHost: localhost").writeTo(socket.getOutputStream())
            val response = http.parseResponse(socket.getInputStream()).eagerly(true)

            response.statusCode shouldBe 200

            // the server should persist the connection
            socket.assertIsOpen()
        }

        "Server should close connection on HTTP/1.1 requests if 'Connection: close' header is sent" {
            val socket = Socket("0.0.0.0", 8093)
            http.parseRequest("GET /hello HTTP/1.1\r\n" +
                    "Host: localhost\r\n" +
                    "Connection: close").writeTo(socket.getOutputStream())
            val response = http.parseResponse(socket.getInputStream()).eagerly(true)

            response.statusCode shouldBe 200

            // the server should have closed this Socket
            socket.assertIsClosed()
        }

        "Server should close connection on HTTP/1.0 requests" {
            val socket = Socket("0.0.0.0", 8093)
            http.parseRequest("GET /hello HTTP/1.0\r\nHost: localhost").writeTo(socket.getOutputStream())
            val response = http.parseResponse(socket.getInputStream()).eagerly(true)

            response.statusCode shouldBe 200

            // the server should have closed this Socket
            socket.assertIsClosed()
        }

        "Server should persist connection on HTTP/1.0 requests if 'Connection: keep-alive' header is sent" {
            val socket = Socket("0.0.0.0", 8093)
            http.parseRequest("GET /hello HTTP/1.0\r\n" +
                    "Host: localhost\r\n" +
                    "Connection: keep-alive").writeTo(socket.getOutputStream())
            val response = http.parseResponse(socket.getInputStream()).eagerly(true)

            response.statusCode shouldBe 200

            // the server should persist the connection
            socket.assertIsOpen()
        }

        "Server honours http client request desire to use 100-Continue" {
            val request = http.parseRequest("POST / HTTP/1.1\n" +
                    "Host: localhost:8093\n" +
                    "Expect: 100-continue\n" +
                    "Content-Length: 10\n" +
                    "\n" +
                    "0123456789").eagerly()

            val socket = Socket(InetAddress.getLoopbackAddress(), 8093)
            socket.soTimeout = 500

            // don't send the body just yet
            val out = socket.getOutputStream()
            Thread {
                request.startLine.writeTo(out)
                request.headers.writeTo(out)
            }.start()

            val response100 = http.parseResponse(socket.getInputStream()).eagerly()

            response100.statusCode shouldBe 100
            response100.headers["Accept-100"] shouldBe listOf("True")
            response100.body should notBePresent()

            // Server accepted the body, send it
            request.body.map { it.writeTo(out) }

            // now the final response can be read
            val response = http.parseResponse(socket.getInputStream()).eagerly()

            response.statusCode shouldBe 200
            response.headers shouldHave validDateHeader()
            response.body should bePresent {
                it.asRawString(StandardCharsets.UTF_8) shouldEqual "Thanks"
            }
        }
    }

    private fun Socket.assertIsOpen() {
        if (isClosed) {
            fail("Expected Socket to be open, but it has been closed")
        }
        soTimeout = 250
        http.parseRequest("GET /is-open HTTP/1.1\r\nHost: localhost").writeTo(getOutputStream())
        try {
            if (getInputStream().read() < 0) {
                fail("Expected Socket to be open, but it appears to have been closed remotely")
            }
            close()
        } catch (e: SocketException) {
            if (e.message == "Connection reset") {
                fail("Expected Socket to be open, but it appears to have been closed remotely")
            } else {
                throw e
            }
        }
    }

    private fun Socket.assertIsClosed() {
        if (!isClosed) {
            // check if the server closed it
            soTimeout = 250
            try {
                http.parseRequest("GET /is-open HTTP/1.1\r\nHost: localhost").writeTo(getOutputStream())
                if (getInputStream().read() > 0) {
                    fail("Expected Socket to be closed, but it seems to still be open")
                }
            } catch (e: SocketException) {
                // SocketException is expected when the socket is closed
            }
        }
    }

}