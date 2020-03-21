package rawhttp.core.client

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test
import rawhttp.core.RawHttp
import kotlin.test.fail

class RedirectingRawHttpClientTest {

    val http = RawHttp()

    @Test
    fun followsRedirect() {
        val redirect = http.parseResponse("302 Found\nLocation: /foo").eagerly()
        val foo = http.parseResponse("200 OK").eagerly()

        val mockClient = RawHttpClient { req ->
            when (req.uri.path) {
                "/" -> redirect
                "/foo" -> foo
                else -> fail("unexpected request: $req")
            }
        }

        val redirectingClient = RedirectingRawHttpClient(mockClient)

        val actualResponse = redirectingClient.send(
                http.parseRequest("GET /\nHost: myhost"))

        actualResponse shouldBe foo
    }

    @Test
    fun followsMultipleRedirects() {
        val redirect1 = http.parseResponse("302 Found\nLocation: /foo").eagerly()
        val redirect2 = http.parseResponse("302 Found\nLocation: bar").eagerly() // relative to /foo
        val redirect3 = http.parseResponse("302 Found\nLocation: /foo/bar/foo").eagerly()

        val resource = http.parseResponse("200 OK").eagerly()

        val mockClient = RawHttpClient { req ->
            when (req.uri.path) {
                "/" -> redirect1
                "/foo" -> redirect2
                "/foo/bar" -> redirect3
                "/foo/bar/foo" -> resource
                else -> fail("unexpected request: $req")
            }
        }

        val redirectingClient = RedirectingRawHttpClient(mockClient)

        val actualResponse = redirectingClient.send(
                http.parseRequest("GET /\nHost: myhost"))

        actualResponse shouldBe resource
    }

    @Test
    fun errorOnTooManyRedirects() {
        val redirect1 = http.parseResponse("302 Found\nLocation: /foo").eagerly()
        val redirect2 = http.parseResponse("302 Found\nLocation: bar").eagerly() // relative to /foo
        val redirect3 = http.parseResponse("302 Found\nLocation: /foo/bar/foo").eagerly()

        val resource = http.parseResponse("200 OK").eagerly()

        val mockClient = RawHttpClient { req ->
            when (req.uri.path) {
                "/" -> redirect1
                "/foo" -> redirect2
                "/foo/bar" -> redirect3
                "/foo/bar/foo" -> resource
                else -> fail("unexpected request: $req")
            }
        }

        val redirectingClient = RedirectingRawHttpClient(mockClient, 2)

        shouldThrow<IllegalStateException> {
            redirectingClient.send(http.parseRequest("GET /\nHost: myhost"))
        }.localizedMessage shouldBe "Too many redirects"
    }

}
