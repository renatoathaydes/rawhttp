package rawhttp.samples

import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.client.TcpRawHttpClient.DefaultOptions
import rawhttp.core.server.Router
import rawhttp.core.server.TcpRawHttpServer
import java.net.Socket
import java.net.URI
import java.time.Duration
import java.util.Optional

private const val serverPort = 8191

object TunnelingRouter : Router {
    private val response405: RawHttpResponse<Void> =
        http.parseResponse("405 Method Not Allowed").eagerly()

    private val response204: RawHttpResponse<Void> =
        http.parseResponse("204 No Content").eagerly()

    override fun route(request: RawHttpRequest): Optional<RawHttpResponse<*>> {
        // only respond to HTTP Connect requests,
        // a 2XX status code means the server should call the tunnel method
        val response = if (request.method.equals("CONNECT", ignoreCase = true))
            response204
        else response405
        return Optional.of(response)
    }

    // the server lets us know if we need to start a tunnel for the client
    override fun tunnel(request: RawHttpRequest, client: Socket) {
        // always free the request thread by handling the tunnel on another Thread
        Thread {
            // in this example, we only read 32 bytes
            val input = ByteArray(32)
            client.getInputStream().read(input)
            // revert the bytes and send it back
            input.reverse()
            client.getOutputStream().use {
                it.write(input)
            }
        }.start()
    }
}

class HttpConnectSample {

    companion object {
        private val server = TcpRawHttpServer(serverPort)

        @BeforeAll
        @JvmStatic
        fun setup() {
            server.start(TunnelingRouter)
            RawHttp.waitForPortToBeTaken(serverPort, Duration.ofSeconds(4))
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            server.stop()
        }
    }

    @Test
    fun `Client and Server can handle a CONNECT request`() {
        var clientSocket: Socket? = null
        val client = TcpRawHttpClient(object : DefaultOptions() {
            override fun getSocket(uri: URI): Socket {
                return super.getSocket(uri).apply { clientSocket = this }
            }
        })

        val hostHeader = "Host: localhost:$serverPort"

        // GET request should result in a 405 response
        val getResponse = client.send(http.parseRequest("GET /\r\n$hostHeader")).eagerly()
        println(getResponse)
        getResponse.statusCode shouldBe 405

        // CONNECT request - start tunneling
        val connectResponse = client.send(http.parseRequest("CONNECT /\r\n$hostHeader")).eagerly()
        println(connectResponse)
        connectResponse.statusCode shouldBe 204

        // send a non-HTTP message
        clientSocket!!.getOutputStream().write((1..32).map { it.toByte() }.toByteArray())

        // the server should reverse the message as that's what the Router implementation does
        val tunnelResponse = ByteArray(32).apply {
            clientSocket!!.getInputStream().read(this)
        }

        tunnelResponse.toList() shouldContainExactly (32 downTo 1).map { it.toByte() }
    }
}
