package rawhttp.cookies.persistence

import io.kotest.matchers.longs.shouldBeInRange
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.cookies.persist.FileCookieJar
import rawhttp.cookies.persist.OnWriteFlushStrategy
import java.io.File
import java.net.HttpCookie
import java.net.URI

class FileCookieJarTest {

    @Test
    fun canPersisteCookiesToFile() {
        val cookieJar = FileCookieJar(File.createTempFile("rawhttp-file-cookie-jar", ".temp"), OnWriteFlushStrategy())

        val expiresAt = System.currentTimeMillis() / 1000L + 30L
        cookieJar.add(URI.create("foo.com"), HttpCookie("a", "b").apply { maxAge = 30 })
        cookieJar.add(URI.create("foo.com"), HttpCookie("b", "c").apply { maxAge = 30 })
        cookieJar.add(URI.create("https://bar.com"), HttpCookie("c", "d").apply {
            maxAge = 30
            domain = "foo"
            path = "/path"
            comment = "my cookie"
            commentURL = "me.com"
            portlist = "8080,8081"
            version = 0
            secure = true
        })

        cookieJar.file.readText() shouldBe """
            foo.com
             "a" "b" "" "" "" "" "" "1" "false" "$expiresAt"
             "b" "c" "" "" "" "" "" "1" "false" "$expiresAt"
            https://bar.com
             "c" "d" "foo" "/path" "my cookie" "me.com" "8080,8081" "0" "true" "$expiresAt"

        """.trimIndent()
    }

    @Test
    fun doesNotPersistNonPersistentCookies() {
        val cookieJar = FileCookieJar(File.createTempFile("rawhttp-file-cookie-jar", ".temp"), OnWriteFlushStrategy())

        val expiresAt = System.currentTimeMillis() / 1000L + 30L

        // add one persistent cookie
        cookieJar.add(URI.create("foo.com"), HttpCookie("a", "b").apply { maxAge = 30 })

        // and a few non-persistent cookies
        cookieJar.add(URI.create("foo.com"), HttpCookie("b", "c"))
        cookieJar.add(URI.create("foo.com"), HttpCookie("d", "e").apply {
            maxAge = 60
            discard = true // now, this is non-persistent
        })

        cookieJar.file.readText() shouldBe """
            foo.com
             "a" "b" "" "" "" "" "" "1" "false" "$expiresAt"

        """.trimIndent()

        // but the cookies still need to be stored in-memory
        cookieJar.cookies.map { it.name } shouldBe listOf("a", "b", "d")
    }

    @Test
    fun canLoadCookiesFromFile() {
        val expiresAt = System.currentTimeMillis() / 1000L + 30
        val fileContents = """
            foo.com
             "a" "b" "" "" "" "" "" "1" "false" "$expiresAt"
             "b" "c" "" "" "" "" "" "1" "false" "$expiresAt"
            https://bar.com
             "c" "d" "foo" "/path" "my cookie" "me.com" "8080,8081" "0" "true" "$expiresAt"

        """.trimIndent()

        val file = File.createTempFile("rawhttp-file-cookie-jar", ".temp")
        file.writeText(fileContents)

        val cookieJar = FileCookieJar(file, OnWriteFlushStrategy())

        cookieJar[URI.create("foo.com")] shouldBe listOf(
                HttpCookie("a", "b"), HttpCookie("b", "c"))

        // the equals method of HttpCookie ignores most attributes, so check them
        cookieJar[URI.create("foo.com")].forEach { cookie ->
            cookie.maxAge shouldBeInRange(29L..31L)
        }

        cookieJar[URI.create("https://bar.com")].first().run {
            domain shouldBe "foo"
            path shouldBe "/path"
            comment shouldBe "my cookie"
            commentURL shouldBe "me.com"
            portlist shouldBe "8080,8081"
            version shouldBe 0
            secure shouldBe true
            maxAge shouldBe 30L
        }

        // the default cookieJar implementation ignores the scheme in the URIs
        cookieJar.urIs shouldBe listOf(URI.create("foo.com"), URI.create("http://bar.com"))
    }

    @Test
    fun doesNotLoadExpiredCookiesFromFile() {
        val expiredTime = System.currentTimeMillis() / 1000L
        val nonExpiredTime = expiredTime + 30

        val fileContents = """
            foo.com
             "a" "b" "" "" "" "" "" "1" "false" "$nonExpiredTime"
             "b" "c" "" "" "" "" "" "1" "false" "$expiredTime"
             "c" "d" "" "" "" "" "" "1" "false" "$expiredTime"
             "d" "e" "" "" "" "" "" "1" "false" "$nonExpiredTime"
            
        """.trimIndent()

        val file = File.createTempFile("rawhttp-file-cookie-jar", ".temp")
        file.writeText(fileContents)

        val cookieJar = FileCookieJar(file, OnWriteFlushStrategy())

        // only the non-expired cookies should be retrieved
        cookieJar[URI.create("foo.com")] shouldBe listOf(
                HttpCookie("a", "b"), HttpCookie("d", "e"))
    }

}
