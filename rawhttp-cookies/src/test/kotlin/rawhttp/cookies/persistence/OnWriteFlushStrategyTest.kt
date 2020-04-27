package rawhttp.cookies.persistence

import io.kotlintest.matchers.shouldBe
import io.kotlintest.mock.mock
import org.junit.Test
import rawhttp.cookies.persist.OnWriteFlushStrategy

class OnWriteFlushStrategyTest {
    @Test
    fun onlyWritesOnWriteCount() {
        val strategy = OnWriteFlushStrategy(3)
        var count = 0
        strategy.init { count++ }

        strategy.onUpdate(mock())
        count shouldBe 0
        strategy.onUpdate(mock())
        count shouldBe 0
        strategy.onUpdate(mock())
        count shouldBe 1
        strategy.onUpdate(mock())
        count shouldBe 1
        strategy.onUpdate(mock())
        count shouldBe 1
        strategy.onUpdate(mock())
        count shouldBe 2
        strategy.onUpdate(mock())
        count shouldBe 2
    }

    @Test
    fun byDefaultWritesEveryTime() {
        val strategy = OnWriteFlushStrategy()
        var count = 0
        strategy.init { count++ }

        strategy.onUpdate(mock())
        count shouldBe 1
        strategy.onUpdate(mock())
        count shouldBe 2
        strategy.onUpdate(mock())
        count shouldBe 3
    }
}