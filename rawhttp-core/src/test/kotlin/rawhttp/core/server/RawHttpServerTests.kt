package rawhttp.core.server

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.bePresent
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.client.waitForPortToBeTaken
import java.net.ServerSocket
import java.util.concurrent.Executors

class RawHttpServerTests {

    @Test
    fun ServerCanHandleHttpClientRequest() {
        val http = RawHttp()
        val executor = Executors.newCachedThreadPool({ r ->
            val thread = Thread(r, "tcp-raw-http-server-test")
            thread.isDaemon = true
            thread
        })

        executor.execute {
            val serverSocket = ServerSocket(8092)
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
                    } catch (e: Exception) {
                        e.printStackTrace()
                        client.close()
                    }
                }
            }
        }

        waitForPortToBeTaken(8092)

        val httpClient = TcpRawHttpClient()

        try {
            val request = http.parseRequest("GET http://localhost:8092")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 200
            response.body should bePresent {
                it.asString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
            }
        } finally {
            httpClient.close()
            executor.shutdownNow()
        }
    }

}