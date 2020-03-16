package rawhttp.cookies

import io.kotlintest.matchers.shouldBe
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.TcpRawHttpServer
import java.net.HttpCookie
import java.time.Duration
import java.util.Optional

class ClientCookiesTest {

    companion object {
        val HTTP = RawHttp()
        val port = 8083
        var server: TcpRawHttpServer? = null

        object Responses {
            val ok = HTTP.parseResponse("200 OK\nContent-Length: 0").eagerly()
        }

        @JvmStatic
        @BeforeClass
        fun setupServer() {
            server = TcpRawHttpServer(port).apply {
                start { req ->
                    val uri = req.startLine.uri
                    if (uri.path == "/cookies") {
                        if (req.method == "POST") {
                            val headers = withCookiesFrom(req.body.map {
                                it.decodeBodyToString(Charsets.UTF_8)
                            }.orElse(""))
                            return@start Optional.of<RawHttpResponse<*>>(Responses.ok.withHeaders(headers))
                        }
                    } else if (uri.path == "/headers") {
                        return@start Optional.of(Responses.ok.withHeaders(req.headers))
                    }
                    Optional.empty<RawHttpResponse<*>>()
                }
            }
            waitForPortToBeTaken(port, Duration.ofSeconds(5))
            println("Server running on port $port")
        }

        private fun withCookiesFrom(query: String?): RawHttpHeaders {
            println("Reading body: '$query'")
            return if (query == null) {
                RawHttpHeaders.empty()
            } else {
                val builder = RawHttpHeaders.newBuilder()
                query.lines().forEach { line ->
                    val cookie = HTTP.metadataParser.parseQueryString(line).toCookie()
                    builder.with("Set-Cookie", cookie.toString())
                }
                builder.build()
            }
        }

        private fun Map<String, List<String>>.toCookie(): HttpCookie {
            val cookie = HttpCookie(this["name"].first(), this["value"].first())
            if (containsKey("path")) {
                cookie.path = this["path"].first()
            }
            return cookie
        }

        private fun List<String>?.first(): String {
            return if (this == null || isEmpty()) "" else get(0)
        }

        @JvmStatic
        @AfterClass
        fun stopServer() {
            server?.stop()
        }
    }

    @Test
    fun clientKeepsCookiesBetweenRequests() {
        val client = TcpRawHttpClient(ClientOptionsWithCookies())

        val response = client.send(HTTP.parseRequest("""
            POST http://localhost:$port/cookies HTTP/1.1
        """.trimIndent()).withBody(StringBody(
                "path=/&name=foo&value=bar\n" +
                        "path=/&name=abc&value=def"))).eagerly()

        response.statusCode shouldBe 200

        // verify that the server set the cookies
        val expectedCookie1 = HttpCookie("foo", "bar").apply { path = "/" }
        val expectedCookie2 = HttpCookie("abc", "def").apply { path = "/" }
        response.headers["Set-Cookie"] shouldBe listOf(expectedCookie1.toString(), expectedCookie2.toString())

        // make a normal request that returns the headers we sent
        val headersResponse = client.send(HTTP.parseRequest("GET http://localhost:$port/headers")).eagerly()

        headersResponse.statusCode shouldBe 200

        // the response headers are copied from the last request we sent
        headersResponse.headers["Cookie"] shouldBe listOf("foo=bar; abc=def")
    }

}