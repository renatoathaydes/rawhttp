package rawhttp.core.server

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.time.Duration
import java.util.Optional
import java.util.concurrent.CompletableFuture
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TcpRawHttpServerRestartingTests {

    companion object {
        private const val PORT = 8094

        private val http = RawHttp()

        private val server = TcpRawHttpServer(object : TcpRawHttpServer.TcpRawHttpServerOptions {
            override fun getServerSocket(): ServerSocket {
                return ServerSocket(PORT)
            }

            override fun configureClientSocket(socket: Socket): Socket {
                socket.soTimeout = 1000
                return socket
            }
        })
        private val clientSockets = LinkedBlockingQueue<Socket>()

        private val httpClient = TcpRawHttpClient(object : TcpRawHttpClient.DefaultOptions() {
            // do not use cached sockets, ignore URI as this test only uses one server
            override fun getSocket(uri: URI): Socket {
                return createSocket(false, uri.host!!, PORT)
            }

            override fun createSocket(useHttps: Boolean, host: String?, port: Int): Socket {
                return super.createSocket(useHttps, host, port).also {
                    // timeout clients after just 1 second of inactivity
                    it.soTimeout = 3000
                    clientSockets.add(it)
                }
            }
        })

        @JvmStatic
        @AfterAll
        fun afterSpec() {
            cleanup()
        }

        private fun startServer() {
            server.start {
                Optional.of(
                    http.parseResponse("200 OK").eagerly()
                        .withBody(StringBody("text/plain", "Hello RawHTTP"))
                )
            }
            waitForPortToBeTaken(PORT, Duration.ofSeconds(2))
        }

        private fun stopServer() {
            server.stop()
        }

        private fun cleanup() {
            server.stop()
            httpClient.close()
        }
    }

    @AfterEach
    fun clearClientSockets() {
        clientSockets.clear()
        stopServer()
    }

    @Test
    fun `Server should kill all client sockets when stopped`() {
        startServer()

        val request = http.parseRequest("GET http://localhost:$PORT/")
        val response = httpClient.send(request).eagerly()

        response.statusCode shouldBe 200
        clientSockets.size shouldBe 1
        val socket = clientSockets.first()
        socket.isClosed shouldBe false

        stopServer()

        socket.shouldBeClosedWithin(Duration.ofSeconds(1))
    }

    @Test
    fun `Server should close client Socket on inactivity from client`() {
        startServer()

        val request = http.parseRequest("GET http://localhost:$PORT/")
        val response = httpClient.send(request).eagerly()

        response.statusCode shouldBe 200
        clientSockets.size shouldBe 1
        val socket = clientSockets.first()
        socket.isClosed shouldBe false

        // let the server timeout the on client Socket read
        socket.shouldBeClosedWithin(Duration.ofSeconds(2))
    }

    private fun Socket.shouldBeClosedWithin(timeout: Duration) {
        val result = CompletableFuture<Boolean>()
        Thread {
            try {
                val b = getInputStream().read()
                if (b == -1) {
                    result.complete(true)
                } else {
                    result.complete(false)
                }
            } catch (e: Exception) {
                result.completeExceptionally(e)
            }
        }.start()

        val ok = result.get(timeout.toMillis(), TimeUnit.MILLISECONDS)
        if (!ok) {
            fail("Socket was not closed")
        }
    }

}