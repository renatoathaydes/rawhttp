package rawhttp.core.server

import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.errors.InvalidHttpRequest
import java.net.ServerSocket
import java.time.Duration
import java.util.concurrent.Executors

class RawHttpServerTests {

    @Test
    fun serverCanHandleHttpClientRequest() {
        val http = RawHttp()
        val executor = Executors.newCachedThreadPool { r ->
            val thread = Thread(r, "tcp-raw-http-server-test")
            thread.isDaemon = true
            thread
        }

        // grab a random port
        val serverSocket = ServerSocket(0)
        val port = serverSocket.localPort

        executor.execute {
            while (true) {
                val client = serverSocket.accept()
                executor.execute {
                    try {
                        val request = http.parseRequest(client.getInputStream())
                        if (request.method == "GET") {
                            http.parseResponse("HTTP/1.1 200 OK\r\n" +
                                    "Content-Type: text/plain"
                            ).withBody(
                                    StringBody("Hello RawHTTP!")
                            ).writeTo(client.getOutputStream())
                        } else {
                            http.parseResponse("HTTP/1.1 405 Method Not Allowed\r\n" +
                                    "Content-Type: text/plain"
                            ).withBody(
                                    StringBody("Sorry, can't handle this request")
                            ).writeTo(client.getOutputStream())
                        }
                    } catch (e: InvalidHttpRequest) {
                        // error ok as we probe the port below without sending an actual request
                        client.close()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        client.close()
                    }
                }
            }
        }

        waitForPortToBeTaken(port, Duration.ofSeconds(2))

        val httpClient = TcpRawHttpClient()

        try {
            val request = http.parseRequest("GET http://localhost:$port")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 200
            response.body shouldBePresent {
                it.asRawString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
            }
        } finally {
            httpClient.close()
            executor.shutdownNow()
        }
    }

}