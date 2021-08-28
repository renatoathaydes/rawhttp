package rawhttp.samples

import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpOptions
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.TcpRawHttpServer
import java.net.ServerSocket
import java.time.Duration
import java.util.Optional

private const val serverPort = 8094

val rawHttpWithFlexibleHeaderValues = RawHttp(
    RawHttpOptions.newBuilder()
        .withHttpHeadersOptions()
        .withValuesCharset(Charsets.UTF_8)
        .done()
        .build()
)

class ServerAndClientRawHttp {

    companion object {
        private val server = TcpRawHttpServer(object : TcpRawHttpServer.TcpRawHttpServerOptions {
            override fun getServerSocket() = ServerSocket(serverPort)
            override fun getRawHttp() = rawHttpWithFlexibleHeaderValues
        })

        private val response200: RawHttpResponse<Void> =
            rawHttpWithFlexibleHeaderValues.parseResponse("200 OK")

        @BeforeAll
        @JvmStatic
        fun setup() {
            server.start { req ->
                // this example doesn't care about path, it just returns the request headers as the body
                Optional.of(response200.withBody(StringBody(req.headers.toString(), "text/plain")))
            }
            RawHttp.waitForPortToBeTaken(serverPort, Duration.ofSeconds(4))
        }

        @AfterAll
        @JvmStatic
        fun cleanup() {
            server.stop()
        }
    }

    @Test
    fun canUseHttpHeaderValueWithSpecificEncoding() {
        val client = TcpRawHttpClient()

        val req = rawHttpWithFlexibleHeaderValues.parseRequest(
            """
                GET / HTTP/1.1
                Accept: こんにちは, text/plain
                User-Agent: RawHTTP
                Host: localhost:$serverPort
            """.trimIndent()
        )

        val res = client.send(req)

        res.run {
            statusCode shouldBe 200
            body shouldBePresent { b ->
                b.decodeBodyToString(Charsets.UTF_8) shouldBe "" +
                        "Accept: こんにちは, text/plain\r\n" +
                        "User-Agent: RawHTTP\r\n" +
                        "Host: localhost:${serverPort}\r\n"
            }
        }
    }

}