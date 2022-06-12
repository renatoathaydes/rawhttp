package rawhttp.core.client

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.internal.TlsCertificateIgnorer
import javax.net.ssl.SSLHandshakeException

class UnsafeTlsClient {
    @Test
    fun canAcceptUntrustedRootTlsCertificateIfOptIn() {
        val req = RawHttp().parseRequest("GET https://untrusted-root.badssl.com/").eagerly()

        // doesn't work by default
        val client = TcpRawHttpClient()

        shouldThrow<SSLHandshakeException> { client.send(req) }

        val unsafeClient = TcpRawHttpClient(TlsCertificateIgnorer.UnsafeHttpClientOptions())

        val res2 = unsafeClient.send(req)

        res2.statusCode shouldBe 200
    }

    @Test
    fun canAcceptSelfSignedTlsCertificateIfOptIn() {
        val req = RawHttp().parseRequest("GET https://self-signed.badssl.com/").eagerly()

        // doesn't work by default
        val client = TcpRawHttpClient()

        shouldThrow<SSLHandshakeException> { client.send(req) }

        val unsafeClient = TcpRawHttpClient(TlsCertificateIgnorer.UnsafeHttpClientOptions())

        val res2 = unsafeClient.send(req)

        res2.statusCode shouldBe 200
    }
}