package rawhttp.cookies

import io.kotlintest.matchers.shouldBe
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.TcpRawHttpServer
import java.net.HttpCookie
import java.time.Duration
import java.util.Optional

class ServerCookiesTest {

    companion object {
        val HTTP = RawHttp()
        val port = 8084
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
                    }
                    Optional.empty()
                }
            }
            RawHttp.waitForPortToBeTaken(port, Duration.ofSeconds(5))
            println("Server running on port $port")
        }

        private fun withCookiesFrom(query: String?): RawHttpHeaders {
            return if (query == null) {
                RawHttpHeaders.empty()
            } else {
                val builder = RawHttpHeaders.newBuilder()
                query.lines().forEach { line ->
                    val (cookie, sameSite) = HTTP.metadataParser.parseQueryString(line).toCookie()
                    ServerCookieHelper.withCookie(builder, cookie, sameSite)
                }
                builder.build()
            }
        }

        private fun Map<String, List<String>>.toCookie(): Pair<HttpCookie, SameSite?> {
            val cookie = HttpCookie(this["name"].first(), this["value"].first())
            if (containsKey("path")) cookie.path = this["path"].first()
            if (containsKey("domain")) cookie.domain = this["domain"].first()
            if (containsKey("maxAge")) cookie.maxAge = this["maxAge"].first().toLong()
            if (containsKey("secure")) cookie.secure = true
            if (containsKey("httpOnly")) cookie.isHttpOnly = true
            val sameSite: SameSite? = if (containsKey("sameSite")) SameSite.parse(this["sameSite"].first()) else null
            return cookie to sameSite
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
    fun serverCanSendCookies() {
        val client = TcpRawHttpClient()

        val response = client.send(HTTP.parseRequest("""
            POST http://localhost:${port}/cookies HTTP/1.1
        """.trimIndent()).withBody(StringBody(
                "name=foo&value=bar\n" +
                        "path=/&name=abc&value=def&sameSite=lax&maxAge=100&secure&httpOnly"))).eagerly()

        response.statusCode shouldBe 200

        response.headers["Set-Cookie"] shouldBe listOf(
                """foo="bar"""",
                """abc="def";Path=/;Max-Age=100;SameSite=Lax;Secure;HttpOnly""")
    }
}