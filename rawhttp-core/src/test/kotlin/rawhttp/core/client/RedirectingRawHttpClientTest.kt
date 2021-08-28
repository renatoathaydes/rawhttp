package rawhttp.core.client

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpRequest
import java.net.URI

// a real HTTP response send by GitHub
const val LARGE_REDIRECT = """
HTTP/1.1 302 Found
date: Thu, 30 Apr 2020 18:12:51 GMT
content-type: text/html; charset=utf-8
server: GitHub.com
status: 302 Found
vary: X-PJAX, Accept-Encoding, Accept, X-Requested-With
location: https://github-production-release-asset-2e65be.s3.amazonaws.com/40832723/b9093080-87e1-11ea-88bd-caad5f5731ae?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=AKIAIWNJYAX4CSVEH53A%2F20200430%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Date=20200430T181251Z&X-Amz-Expires=300&X-Amz-Signature=a74251d36e1c2a3fc28c3644084e1e7f0e5e8e499d53feb18c1499a2b4dd2694&X-Amz-SignedHeaders=host&actor_id=0&repo_id=40832723&response-content-disposition=attachment%3B%20filename%3Dlogfx-1.0-RC11-linux.zip&response-content-type=application%2Foctet-stream
cache-control: no-cache
strict-transport-security: max-age=31536000; includeSubdomains; preload
x-frame-options: deny
x-content-type-options: nosniff
x-xss-protection: 1; mode=block
expect-ct: max-age=2592000, report-uri="https://api.github.com/_private/browser/errors"
content-security-policy: default-src 'none'; base-uri 'self'; block-all-mixed-content; connect-src 'self' uploads.github.com www.githubstatus.com collector.githubapp.com api.github.com www.google-analytics.com github-cloud.s3.amazonaws.com github-production-repository-file-5c1aeb.s3.amazonaws.com github-production-upload-manifest-file-7fdce7.s3.amazonaws.com github-production-user-asset-6210df.s3.amazonaws.com cdn.optimizely.com logx.optimizely.com/v1/events wss://live.github.com; font-src github.githubassets.com; form-action 'self' github.com gist.github.com; frame-ancestors 'none'; frame-src render.githubusercontent.com; img-src 'self' data: github.githubassets.com identicons.github.com collector.githubapp.com github-cloud.s3.amazonaws.com *.githubusercontent.com; manifest-src 'self'; media-src 'none'; script-src github.githubassets.com; style-src 'unsafe-inline' github.githubassets.com
Set-Cookie: _gh_sess=rTz6jVbx7AqQawgnAmgds9HJnqfhr4AXpqnfumMCQBJo5d2XGRfYA%2BeHMxaR0rC6IIxC8z6Dx3jRTk8B%2FSU%2FQkOxGzwOdLSN2FTJ1p1JXAfrxlkTRi%2BBWTk72FzDsxfR8e2OkBmUa%2Bnp467%2Bs62y0iHBnN0MWIc4bYPtoQd%2BC3SPBBn9%2Fz7QSF%2Bn9yc%2BVbS0ObbylMvoIKpxifxiBNMlfara7FhzP2m%2BdXkMdhtPZhequV9k%2B2GJg%2BZ%2BksfJIyfII7URUX%2BdHrWMspmxqLzz%2Bw%3D%3D--D9CwZJgtlg0BSU6y--bTZ660M5vKMSl1CTdFO9zw%3D%3D; Path=/; HttpOnly; Secure
Set-Cookie: _octo=GH1.1.1231482405.1588270370; Path=/; Domain=github.com; Expires=Fri, 30 Apr 2021 18:12:50 GMT; Secure
Set-Cookie: logged_in=no; Path=/; Domain=github.com; Expires=Fri, 30 Apr 2021 18:12:50 GMT; HttpOnly; Secure
Content-Length: 63
X-GitHub-Request-Id: C67C:3A637:4FD7AE:6E90D9:5EAB1522

XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"""

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
        }.localizedMessage shouldBe "Too many redirects (2)"
    }

    @Test
    fun canFollowVeryLargeRedirect() {
        val redirect = http.parseResponse(LARGE_REDIRECT).eagerly()
        val foo = http.parseResponse("200 OK").eagerly()

        val location = redirect.headers["Location"].first()

        // confirm we parse it correctly
        location shouldBe "https://github-production-release-asset-2e65be.s3.amazonaws.com" +
                "/40832723/b9093080-87e1-11ea-88bd-caad5f5731ae" +
                "?X-Amz-Algorithm=AWS4-HMAC-SHA256" +
                "&X-Amz-Credential=AKIAIWNJYAX4CSVEH53A%2F20200430%2Fus-east-1%2Fs3%2Faws4_request" +
                "&X-Amz-Date=20200430T181251Z" +
                "&X-Amz-Expires=300" +
                "&X-Amz-Signature=a74251d36e1c2a3fc28c3644084e1e7f0e5e8e499d53feb18c1499a2b4dd2694" +
                "&X-Amz-SignedHeaders=host" +
                "&actor_id=0" +
                "&repo_id=40832723" +
                "&response-content-disposition=attachment%3B%20filename%3Dlogfx-1.0-RC11-linux.zip" +
                "&response-content-type=application%2Foctet-stream"

        val targetPath = URI.create(location).path
        var redirectRequest: RawHttpRequest? = null

        val mockClient = RawHttpClient { req ->
            when (req.uri.path) {
                "/" -> redirect
                targetPath -> {
                    redirectRequest = req
                    foo
                }
                else -> fail("unexpected request: $req")
            }
        }

        val redirectingClient = RedirectingRawHttpClient(mockClient)

        val actualResponse = redirectingClient.send(
                http.parseRequest("GET /\nHost: myhost\nAccept: application/json"))

        actualResponse shouldBe foo

        redirectRequest!!.headers.asMap() shouldBe mapOf(
                "HOST" to listOf("github-production-release-asset-2e65be.s3.amazonaws.com"),
                "ACCEPT" to listOf("application/json")
        )
        redirectRequest!!.uri.path shouldBe "/40832723/b9093080-87e1-11ea-88bd-caad5f5731ae"
    }

    /**
     * https://tools.ietf.org/html/rfc7238
     *
     * ```
     *  +-------------------------------------------+-----------+-----------+
     *  |                                           | Permanent | Temporary |
     *  +-------------------------------------------+-----------+-----------+
     *  | Allows changing the request method from   | 301       | 302       |
     *  | POST to GET                               |           |           |
     *  | Does not allow changing the request       | (308)     | 307       |
     *  | method from POST to GET                   |           |           |
     *  +-------------------------------------------+-----------+-----------+
     *  ```
     *
     *  303 (See Other) enforces that the redirected request must use GET or HEAD.
     */
    @Test
    fun redirectsMayChangeHttpMethod() {
        val redirect301 = http.parseResponse("301 Moved Permanently\nLocation: /foo").eagerly()
        val redirect302 = http.parseResponse("302 Found\nLocation: /foo").eagerly()
        val redirect303 = http.parseResponse("303 See Other\nLocation: /foo").eagerly()
        val redirect307 = http.parseResponse("307 Temporary Redirect\nLocation: /foo").eagerly()
        val redirect308 = http.parseResponse("308 Permanent Redirect\nLocation: /foo").eagerly()
        val foo = http.parseResponse("200 OK").eagerly()

        val requests = mutableListOf<RawHttpRequest>()

        val mockClient = RawHttpClient { req ->
            requests.add(req)
            when (req.uri.path) {
                "/301" -> redirect301
                "/302" -> redirect302
                "/303" -> redirect303
                "/307" -> redirect307
                "/308" -> redirect308
                "/foo" -> foo
                else -> fail("unexpected request: $req")
            }
        }

        val redirectingClient = RedirectingRawHttpClient(mockClient)

        listOf(301, 302, 303, 307, 308).forEach { code ->
            listOf("GET", "POST", "PUT", "DELETE", "HEAD", "OPTIONS").forEach { method ->
                redirectingClient.send(http.parseRequest("$method /$code\nHost: myhost"))
            }
        }

        val actual = requests.map { it.uri.path to it.method }
        val expected = listOf(
                "/301" to "GET", "/foo" to "GET",
                "/301" to "POST", "/foo" to "POST",
                "/301" to "PUT", "/foo" to "PUT",
                "/301" to "DELETE", "/foo" to "DELETE",
                "/301" to "HEAD", "/foo" to "HEAD",
                "/301" to "OPTIONS", "/foo" to "OPTIONS",

                "/302" to "GET", "/foo" to "GET",
                "/302" to "POST", "/foo" to "POST",
                "/302" to "PUT", "/foo" to "PUT",
                "/302" to "DELETE", "/foo" to "DELETE",
                "/302" to "HEAD", "/foo" to "HEAD",
                "/302" to "OPTIONS", "/foo" to "OPTIONS",

                "/303" to "GET", "/foo" to "GET",
                "/303" to "POST", "/foo" to "GET",
                "/303" to "PUT", "/foo" to "GET",
                "/303" to "DELETE", "/foo" to "GET",
                "/303" to "HEAD", "/foo" to "HEAD",
                "/303" to "OPTIONS", "/foo" to "GET",

                "/307" to "GET", "/foo" to "GET",
                "/307" to "POST", "/foo" to "POST",
                "/307" to "PUT", "/foo" to "PUT",
                "/307" to "DELETE", "/foo" to "DELETE",
                "/307" to "HEAD", "/foo" to "HEAD",
                "/307" to "OPTIONS", "/foo" to "OPTIONS",

                "/308" to "GET", "/foo" to "GET",
                "/308" to "POST", "/foo" to "POST",
                "/308" to "PUT", "/foo" to "PUT",
                "/308" to "DELETE", "/foo" to "DELETE",
                "/308" to "HEAD", "/foo" to "HEAD",
                "/308" to "OPTIONS", "/foo" to "OPTIONS"
        )

        expected.forEachIndexed { i, item ->
            val actualItem = actual[i]
            actualItem shouldBe item
        }

    }

}
