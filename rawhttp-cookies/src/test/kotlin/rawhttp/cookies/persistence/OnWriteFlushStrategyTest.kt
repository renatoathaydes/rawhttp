package rawhttp.cookies.persistence

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.cookies.persist.OnWriteFlushStrategy
import java.net.CookieStore
import java.net.HttpCookie
import java.net.URI

class OnWriteFlushStrategyTest {
    @Test
    fun onlyWritesOnWriteCount() {
        val strategy = OnWriteFlushStrategy(3)
        var count = 0
        strategy.init { count++ }

        strategy.onUpdate(MockCookieStore)
        count shouldBe 0
        strategy.onUpdate(MockCookieStore)
        count shouldBe 0
        strategy.onUpdate(MockCookieStore)
        count shouldBe 1
        strategy.onUpdate(MockCookieStore)
        count shouldBe 1
        strategy.onUpdate(MockCookieStore)
        count shouldBe 1
        strategy.onUpdate(MockCookieStore)
        count shouldBe 2
        strategy.onUpdate(MockCookieStore)
        count shouldBe 2
    }

    @Test
    fun byDefaultWritesEveryTime() {
        val strategy = OnWriteFlushStrategy()
        var count = 0
        strategy.init { count++ }

        strategy.onUpdate(MockCookieStore)
        count shouldBe 1
        strategy.onUpdate(MockCookieStore)
        count shouldBe 2
        strategy.onUpdate(MockCookieStore)
        count shouldBe 3
    }
}

object MockCookieStore : CookieStore {
    override fun add(uri: URI?, cookie: HttpCookie?) {

    }

    override fun get(uri: URI?): List<HttpCookie> {
        return emptyList()
    }

    override fun getCookies(): List<HttpCookie> {
        return emptyList()
    }

    override fun getURIs(): List<URI> {
        return emptyList()
    }

    override fun remove(uri: URI?, cookie: HttpCookie?): Boolean {
        return false
    }

    override fun removeAll(): Boolean {
        return false
    }

}