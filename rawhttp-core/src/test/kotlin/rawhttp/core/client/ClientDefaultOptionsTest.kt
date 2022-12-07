package rawhttp.core.client

import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.matchers.types.shouldNotBeSameInstanceAs
import org.junit.jupiter.api.Test
import rawhttp.core.client.TcpRawHttpClient.DefaultOptions
import java.net.URI

class ClientDefaultOptionsTest {

    @Test
    fun doNotReuseSocketIfSchemeChanges() {
        val options = DefaultOptions()

        val httpSocket1 = options.getSocket(URI.create("http://example.org"))
        val httpsSocket1 = options.getSocket(URI.create("https://example.org"))
        val httpSocket2 = options.getSocket(URI.create("http://example.org"))
        val httpsSocket2 = options.getSocket(URI.create("https://example.org"))

        httpSocket1 shouldBeSameInstanceAs httpSocket2
        httpsSocket1 shouldBeSameInstanceAs httpsSocket2

        httpSocket1 shouldNotBeSameInstanceAs httpsSocket1
        httpSocket2 shouldNotBeSameInstanceAs httpsSocket2
    }

}
