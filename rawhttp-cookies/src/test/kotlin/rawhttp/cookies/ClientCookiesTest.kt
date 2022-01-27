package rawhttp.cookies

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.TcpRawHttpServer
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.Socket
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
        @BeforeAll
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
                    } else if (uri.path == "/headers" || uri.path == "/headers/two") {
                        return@start Optional.of(Responses.ok.withHeaders(req.headers))
                    }
                    Optional.empty()
                }
            }
            waitForPortToBeTaken(port, Duration.ofSeconds(5))
            println("Server running on port $port")
        }

        private fun withCookiesFrom(query: String?): RawHttpHeaders {
            return if (query == null) {
                RawHttpHeaders.empty()
            } else {
                val builder = RawHttpHeaders.newBuilder()
                query.lines().forEach { line ->
                    val cookie = HTTP.metadataParser.parseQueryString(line).toCookie()
                    ServerCookieHelper.setCookie(builder, cookie)
                }
                builder.build()
            }
        }

        private fun Map<String, List<String>>.toCookie(): HttpCookie {
            val cookie = HttpCookie(this["name"].first(), this["value"].first())
            if (containsKey("path")) cookie.path = this["path"].first()
            if (containsKey("domain")) cookie.domain = this["domain"].first()
            if (containsKey("maxAge")) cookie.maxAge = this["maxAge"].first().toLong()
            if (containsKey("secure")) cookie.secure = true
            if (containsKey("httpOnly")) cookie.isHttpOnly = true
            return cookie
        }

        private fun List<String>?.first(): String {
            return if (this == null || isEmpty()) "" else get(0)
        }

        @JvmStatic
        @AfterAll
        fun stopServer() {
            server?.stop()
        }
    }

    @Test
    fun clientKeepsCookiesBetweenRequests() {
        val client = TcpRawHttpClient(ClientOptionsWithCookies())

        // ask the server to set a couple of cookies
        val response = client.send(HTTP.parseRequest("""
            POST http://localhost:$port/cookies HTTP/1.1
        """.trimIndent()).withBody(StringBody(
                "path=/&name=foo&value=bar\n" +
                        "path=/&name=abc&value=def"))).eagerly()

        response.statusCode shouldBe 200

        response.headers["Set-Cookie"] shouldBe listOf(
                """foo="bar"; Path=/""",
                """abc="def"; Path=/""")

        // make a normal request that returns the headers we sent
        val headersResponse = client.send(HTTP.parseRequest("GET http://localhost:$port/headers")).eagerly()

        headersResponse.statusCode shouldBe 200

        // the response headers should show that the HTTP client correctly sent the relevant cookies
        headersResponse.headers["Cookie"] shouldBe listOf("foo=bar; abc=def")
    }

    /**
     * According to MDN <a href="https://developer.mozilla.org/en-US/docs/Web/HTTP/Headers/Set-Cookie">Set-Cookie</a>
     * docs:
     *
     * If (domain is) omitted, defaults to the host of the current document URL, not including subdomains.
     * Contrary to earlier specifications, leading dots in domain names (.example.com) are ignored.
     * If a domain is specified, subdomains are always included.
     * A cookie for a domain that does not include the server that set it should be rejected by the user agent.
     * A cookie for a sub domain of the serving domain will be rejected.
     *
     */
    @Test
    fun clientDoesNotSendCookiesForWrongDomainOrPath() {
        // this client will just send all requests to localhost, regardless of the domain
        val client = TcpRawHttpClient(ClientOptionsWithCookies(
                CookieManager(null, CookiePolicy.ACCEPT_ALL), ClientOptionsIgnoresDomain()))

        // ask the server to set a couple of cookies
        val response = client.send(HTTP.parseRequest("""
            POST http://a.b.localhost:$port/cookies HTTP/1.1
        """.trimIndent()).withBody(StringBody(
                "path=/headers&name=foo&value=bar&domain=b.localhost\n" +
                        "path=/headers/two&name=abc&value=def"))).eagerly()

        response.statusCode shouldBe 200

        response.headers["Set-Cookie"] shouldBe listOf(
                """foo="bar"; Domain=b.localhost; Path=/headers""",
                """abc="def"; Path=/headers/two""")

        data class Ex(val domain: String, val path: String, val expectedCookies: Set<String>)

        val examples = listOf(
                Ex(domain = "localhost", path = "headers", expectedCookies = setOf()),
                Ex(domain = "localhost", path = "headers/two", expectedCookies = setOf()),
                Ex(domain = "c.localhost", path = "headers/two", expectedCookies = setOf()),
                Ex(domain = "other-domain", path = "headers", expectedCookies = setOf()),
                Ex(domain = "b.localhost", path = "headers", expectedCookies = setOf("foo=bar")),
                Ex(domain = "b.localhost", path = "headers/two", expectedCookies = setOf("foo=bar")),
                Ex(domain = "a.b.localhost", path = "headers", expectedCookies = setOf("foo=bar")),
                Ex(domain = "a.b.localhost", path = "headers/two", expectedCookies = setOf("foo=bar", "abc=def"))
        )

        for ((domain, path, expectedCookies) in examples) {
            // make a normal request that returns the headers we sent
            val headersResponse = client.send(HTTP.parseRequest("GET http://$domain:$port/$path")).eagerly()

            headersResponse.statusCode shouldBe 200

            headersResponse.headers["Cookie"].flatMap {
                it.split(";").map { s -> s.trim() }
            }.toSet() shouldBe expectedCookies
        }
    }

}

class ClientOptionsIgnoresDomain : TcpRawHttpClient.DefaultOptions() {
    override fun createSocket(useHttps: Boolean, host: String, port: Int): Socket {
        return Socket("localhost", port)
    }
}