package rawhttp.samples

import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpOptions
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.TcpRawHttpServer
import java.net.Socket
import java.net.SocketException
import java.util.*

object TargetServer {
    private val http = RawHttp(RawHttpOptions.strict())
    private val server = TcpRawHttpServer(8080)

    private val okResponse: RawHttpResponse<Void> = http.parseResponse("200 OK")
        .withBody(StringBody("Hello!", "text/plain; charset=utf-8"))
        .eagerly()
    private val notFoundResponse: RawHttpResponse<Void> = http.parseResponse("404 Not Found")
        .withBody(StringBody("Nothing here!", "text/plain; charset=utf-8"))
        .eagerly()
    private val notAllowedResponse: RawHttpResponse<Void> = http.parseResponse("401 Not Allowed")
        .withBody(StringBody("This request is not allowed", "text/plain; charset=utf-8"))
        .eagerly()

    @JvmStatic
    fun main(args: Array<String>) {
        server.start { request ->
            println("Request: ${request.eagerly()}")
            when (request.uri.path) {
                "/example" -> Optional.of(okResponse)
                "/404" -> Optional.of(notFoundResponse)
                else -> Optional.of(notAllowedResponse)
            }
        }
    }

    fun stop() {
        server.stop()
    }

}

class MaliciousClientTest {
    private val http = RawHttp(RawHttpOptions.strict())

    private val get = http.parseRequest(
        "GET http://localhost:8080/example HTTP/1.1\r\n" +
                "Host: localhost:8080"
    ).eagerly()

    private val getWithBody = get.withBody(StringBody("GET /404 HTTP/1.1")).eagerly()

    private val optionsWithBody = http.parseRequest(
        "OPTIONS http://localhost:8080/example HTTP/1.1\r\n" +
                "Host: localhost:8080"
    ).withBody(StringBody("GET /404 HTTP/1.1")).eagerly()

    companion object {
        private val client = TcpRawHttpClient()

        @BeforeAll
        @JvmStatic
        fun init() {
            TargetServer.main(emptyArray())
        }

        @AfterAll
        @JvmStatic
        fun destroy() {
            TargetServer.stop()
            client.close()
        }
    }

    @Test
    fun `GET with malicious body is not confused with 2 requests`() {
        val response = client.send(getWithBody).eagerly()
        assertEquals(200, response.statusCode)
        assertTrue(response.body.isPresent)
        assertEquals("Hello!", response.body.get().decodeBodyToString(Charsets.UTF_8))
    }

    @Test
    fun `OPTIONS with malicious body is not confused with 2 requests`() {
        val response = client.send(optionsWithBody).eagerly()
        assertEquals(200, response.statusCode)
        assertTrue(response.body.isPresent)
        assertEquals("Hello!", response.body.get().decodeBodyToString(Charsets.UTF_8))
    }

    @Test
    fun `Invalid header syntax is not tolerated (whitespace before colon)`() {
        val socket = Socket("localhost", 8080)
        socket.outputStream.write(
            ("GET /hello HTTP/1.1\r\n" +
                    "Host : localhost\r\n\r\n").toByteArray()
        )
        try {
            shouldThrow<SocketException> {
                http.parseResponse(socket.inputStream)
            }
        } finally {
            socket.close()
        }

        // but the HTTP Server is still ok
        `GET with malicious body is not confused with 2 requests`()
    }

    @Test
    fun `Invalid header syntax is not tolerated (invalid character in header value)`() {
        val socket = Socket("localhost", 8080)
        socket.outputStream.write(
            ("GET /hello HTTP/1.1\r\n" +
                    "Host : localhost\r\n" +
                    "Content-Length: \n 36\r\n\r\nGET /404 HTTP/1.1\r\nHost: localhost\r\n").toByteArray()
        )
        try {
            shouldThrow<SocketException> {
                http.parseResponse(socket.inputStream)
            }
        } finally {
            socket.close()
        }

        // but the HTTP Server is still ok
        `GET with malicious body is not confused with 2 requests`()
    }
}
