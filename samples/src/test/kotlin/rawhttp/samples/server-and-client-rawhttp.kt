package rawhttp.samples

import org.hamcrest.CoreMatchers.equalTo
import org.junit.AfterClass
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpOptions
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.TcpRawHttpServer
import java.net.ServerSocket
import java.time.Duration
import java.util.Optional

const val serverPort = 8094

val rawHttpWithFlexibleHeaderValues = RawHttp(RawHttpOptions.newBuilder()
        .withHttpHeadersOptions()
        .withValuesCharset(Charsets.UTF_8)
        .done()
        .build())

class ServerAndClientRawHttp {

    companion object {
        val server = TcpRawHttpServer(object : TcpRawHttpServer.TcpRawHttpServerOptions {
            override fun getServerSocket() = ServerSocket(serverPort)
            override fun getRawHttp() = rawHttpWithFlexibleHeaderValues
        })

        val response200 = rawHttpWithFlexibleHeaderValues.parseResponse("200 OK")

        @BeforeClass
        @JvmStatic
        fun setup() {
            server.start { req ->
                // this example doesn't care about path, it just returns the request headers as the body
                println("Received headers ${req.headers}")
                Optional.of(response200.withBody(StringBody(req.headers.toString(), "text/plain")))
            }
            RawHttp.waitForPortToBeTaken(serverPort, Duration.ofSeconds(4))
        }

        @AfterClass
        @JvmStatic
        fun cleanup() {
            server.stop()
        }
    }

    @Test
    fun canUseHttpHeaderValueWithSpecificEncoding() {
        val client = TcpRawHttpClient()

        val req = rawHttpWithFlexibleHeaderValues.parseRequest("""
                GET / HTTP/1.1
                Accept: こんにちは, text/plain
                User-Agent: RawHTTP
                Host: localhost:$serverPort""".trimIndent())

        val res = client.send(req)

        res.run {
            assertThat(statusCode, equalTo(200))
            assertTrue(body.isPresent)
            assertThat(body.get().decodeBodyToString(Charsets.UTF_8),
                    equalTo("Accept: こんにちは, text/plain\r\n" +
                            "User-Agent: RawHTTP\r\n" +
                            "Host: localhost:${serverPort}\r\n"))
        }
    }

}