package rawhttp.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.net.URI

class UriUtilTest {

    @Test
    fun replaceScheme() {
        UriUtil.withScheme(URI(null, "foo.bar", "/path", null), "http") shouldBe URI.create("http://foo.bar/path")
        UriUtil.withScheme(URI.create("http://foo.bar/path"), "https") shouldBe URI.create("https://foo.bar/path")
        UriUtil.withScheme(URI.create("ftp:///me.txt"), "https") shouldBe URI.create("https:///me.txt")
    }

    @Test
    fun canReplaceUserInfo() {
        UriUtil.withUserInfo(URI("https", "me:pass", "foo.com", 84, "/resource", null, null), "joe:123") shouldBe
                URI("https", "joe:123", "foo.com", 84, "/resource", null, null)
        UriUtil.withUserInfo(URI(null, "me", "foo.com", 80, "/resource", null, null), "joe") shouldBe
                URI("http", "joe", "foo.com", 80, "/resource", null, null)
    }

    @Test
    fun canReplaceHost() {
        UriUtil.withHost(URI.create("http://foo/path"), "bar") shouldBe URI.create("http://bar/path")
        UriUtil.withHost(URI.create("http://foo:84/path"), "bar") shouldBe URI.create("http://bar:84/path")
        UriUtil.withHost(URI.create("http://foo:84/path"), "bar:85") shouldBe URI.create("http://bar:85/path")
        UriUtil.withHost(URI.create("https://foo/path?x=1"), "x.com") shouldBe URI.create("https://x.com/path?x=1")
    }

    @Test
    fun canReplacePort() {
        UriUtil.withPort(URI.create("http://foo/path"), 8082) shouldBe URI.create("http://foo:8082/path")
        UriUtil.withPort(URI.create("https://foo.com:8333/?x=1"), 8445) shouldBe URI.create("https://foo.com:8445/?x=1")
    }

    @Test
    fun canReplacePath() {
        UriUtil.withPath(URI.create("http://foo"), "/path") shouldBe URI.create("http://foo/path")
        UriUtil.withPath(URI.create("http://foo/"), "/path") shouldBe URI.create("http://foo/path")
        UriUtil.withPath(URI.create("http://foo/p1"), "/path") shouldBe URI.create("http://foo/path")
        UriUtil.withPath(URI.create("https://foo/p1/p2"), "/p2/p1") shouldBe URI.create("https://foo/p2/p1")
        UriUtil.withPath(URI.create("http://foo/p1/p2?x=1"), "/p3") shouldBe URI.create("http://foo/p3?x=1")

        UriUtil.withPath(URI.create("http://foo/p1/p2#x=1"), "") shouldBe URI.create("http://foo#x=1")
        UriUtil.withPath(URI.create("http://foo/p1/p2#x=1"), "").path shouldBe ""
        UriUtil.withPath(URI.create("http://foo/p1/p2#x=1"), "").fragment shouldBe "x=1"
    }

    @Test
    fun canReplaceQuery() {
        UriUtil.withQuery(URI.create("http://foo"), "x=1") shouldBe URI.create("http://foo?x=1")
        UriUtil.withQuery(URI.create("http://foo"), "") shouldBe URI.create("http://foo")
        UriUtil.withQuery(URI.create("http://foo/p#q=w"), "") shouldBe URI.create("http://foo/p#q=w")
        UriUtil.withQuery(URI.create("http://foo/p?q=w"), "") shouldBe URI.create("http://foo/p")
        UriUtil.withQuery(URI.create("https://foo/p?q=w#me"), "x=2&y=3") shouldBe URI.create("https://foo/p?x=2&y=3#me")
    }

    @Test
    fun canReplaceFragment() {
        UriUtil.withFragment(URI.create("http://foo"), "x=1") shouldBe URI.create("http://foo#x=1")
        UriUtil.withFragment(URI.create("http://foo"), "") shouldBe URI.create("http://foo")
        UriUtil.withFragment(URI.create("http://foo/p?q=w"), "") shouldBe URI.create("http://foo/p?q=w")
        UriUtil.withFragment(URI.create("http://foo/p#q=w"), "") shouldBe URI.create("http://foo/p")
        UriUtil.withFragment(URI.create("https://foo/p?q=w#me"), "x=2&y=3") shouldBe URI.create("https://foo/p?q=w#x=2&y=3")
    }

    @Test
    fun canBuildURI() {
        data class Ex(val scheme: String?, val userInfo: String?, val host: String?,
                      val path: String?, val query: String?, val fragment: String?,
                      val result: String)

        val examples = listOf(
                Ex(null, null, null, "/path", null, null, "http:///path"),
                Ex("https", null, "foo", null, null, null, "https://foo"),
                Ex("http", null, "me.com", "/", null, null, "http://me.com/"),
                Ex("http", null, null, "/", null, null, "http:///"),
                Ex("http", "me:se", "me.com", "/", null, null, "http://me:se@me.com/"),
                Ex("http", "me:se", "me.com", "/foo", "x=y&z=w", null, "http://me:se@me.com/foo?x=y&z=w"),
                Ex("http", "me:se", "me.com", "/foo", "x=y&z=w", "bar", "http://me:se@me.com/foo?x=y&z=w#bar"),
                Ex("https", null, "me.com", "/foo", null, "x=3&y=4", "https://me.com/foo#x=3&y=4")
        )

        for ((scheme, userInfo, host, path, query, fragment, result) in examples) {
            val builder = UriUtil.builder()
            if (scheme != null) builder.withScheme(scheme)
            if (userInfo != null) builder.withUserInfo(userInfo)
            if (host != null) builder.withHost(host)
            if (path != null) builder.withPath(path)
            if (query != null) builder.withQuery(query)
            if (fragment != null) builder.withFragment(fragment)

            builder.build() shouldBe URI.create(result)
        }
    }

}