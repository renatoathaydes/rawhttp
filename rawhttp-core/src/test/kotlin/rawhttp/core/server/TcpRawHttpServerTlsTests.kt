package rawhttp.core.server

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.EagerHttpResponse
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.internal.TlsCertificateIgnorer
import rawhttp.core.server.TlsConfiguration.clientOptions
import rawhttp.core.server.TlsConfiguration.createSSLContext
import rawhttp.core.server.TlsConfiguration.serverOptions
import java.time.Duration
import java.util.Optional
import javax.net.ssl.SSLHandshakeException

class TcpRawHttpServerTlsTests {

    companion object {
        const val PORT = 8074
        const val KEYSTORE_PASS = "password"

        private val http = RawHttp()

        // Command used to generate keystore:
        // keytool -genkey -alias rawhttp -keyalg RSA -keystore keystore.jks
        private val sslContext = createSSLContext(
            RawHttp::class.java.getResource("keystore.jks"), KEYSTORE_PASS,
            RawHttp::class.java.getResource("keystore.jks"), KEYSTORE_PASS
        )

        private val server = TcpRawHttpServer(serverOptions(sslContext, PORT))

        private val standardHttpClient = TcpRawHttpClient()

        private val safeHttpClient = TcpRawHttpClient(clientOptions(sslContext))

        private val unsafeHttpClient = TcpRawHttpClient(TlsCertificateIgnorer.UnsafeHttpClientOptions())

        @JvmStatic
        @BeforeAll
        fun beforeSpec() {
            startServer()
            RawHttp.waitForPortToBeTaken(PORT, Duration.ofSeconds(2))
        }

        @JvmStatic
        @AfterAll
        fun afterSpec() {
            cleanup()
        }

        private fun startServer() {
            server.start(TestRouter)
        }

        private fun cleanup() {
            server.stop()
            standardHttpClient.close()
            unsafeHttpClient.close()
            safeHttpClient.close()
        }
    }

    object TestRouter : Router {
        override fun route(req: RawHttpRequest): Optional<RawHttpResponse<*>> {
            return Optional.ofNullable(http.parseResponse("HTTP/1.1 200 OK").withBody(StringBody("TLS")))
        }
    }

    @Test
    fun `Server can handle successful http client request using TLS - unsafe HTTP Client`() {
        val response = requestWith(unsafeHttpClient)
        assertOkResponse(response)
    }

    @Test
    fun `Server can handle successful http client request using TLS - safe HTTP Client`() {
        val response = requestWith(safeHttpClient)
        assertOkResponse(response)
    }

    @Test
    fun `Standard HTTP Client cannot make requests to self-signed certificate Server`() {
        shouldThrow<SSLHandshakeException> { requestWith(standardHttpClient) }
    }

    private fun requestWith(client: TcpRawHttpClient): EagerHttpResponse<Void> {
        val request = http.parseRequest("GET https://localhost:$PORT/")
        return client.send(request).eagerly()
    }

    private fun assertOkResponse(response: RawHttpResponse<*>) {
        response.statusCode shouldBe 200
        response.body shouldBePresent {
            it.asRawString(Charsets.UTF_8) shouldBe "TLS"
        }
    }

}