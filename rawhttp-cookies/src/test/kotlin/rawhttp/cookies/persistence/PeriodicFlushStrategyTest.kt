package rawhttp.cookies.persistence

import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.jupiter.api.Test
import rawhttp.cookies.persist.PeriodicFlushStrategy
import java.time.Duration
import java.util.concurrent.Delayed
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

class PeriodicFlushStrategyTest {

    @Test
    fun flushesPeriodically() {
        var count = 0
        var flushAction: Runnable? = null

        val executor = object : ScheduledExecutorService by Executors.newSingleThreadScheduledExecutor() {
            override fun scheduleAtFixedRate(command: Runnable, initialDelay: Long, period: Long, unit: TimeUnit): ScheduledFuture<*> {
                flushAction = command
                initialDelay shouldBe 1000L
                period shouldBe 1000L
                unit shouldBe TimeUnit.MILLISECONDS
                return MockScheduledFuture
            }

            override fun submit(task: Runnable): Future<*> {
                task.run()
                return MockScheduledFuture
            }
        }

        val strategy = PeriodicFlushStrategy(Duration.ofSeconds(1), executor)

        strategy.init { count++ }

        // make sure the flushAction was set
        flushAction shouldNotBe null

        // and the flush action has never run
        count shouldBe 0

        // WHEN the flush action is executed by the scheduler
        flushAction?.run()

        // THEN flush is not called because there has been no updates to the cookie store
        count shouldBe 0

        // WHEN we update the cookie store
        strategy.onUpdate(MockCookieStore)

        // AND run the flush action
        flushAction?.run()

        // THEN flush should be called
        count shouldBe 1
    }
}

object MockScheduledFuture: ScheduledFuture<Unit> {
    override fun compareTo(other: Delayed?): Int {
        return -1
    }

    override fun getDelay(unit: TimeUnit): Long {
        return 0L
    }

    override fun cancel(mayInterruptIfRunning: Boolean): Boolean {
        return false
    }

    override fun isCancelled(): Boolean {
        return false
    }

    override fun isDone(): Boolean {
        return false
    }

    override fun get() {
    }

    override fun get(timeout: Long, unit: TimeUnit) {
    }

}