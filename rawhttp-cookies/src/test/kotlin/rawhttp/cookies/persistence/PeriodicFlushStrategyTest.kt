package rawhttp.cookies.persistence

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.mock.mock
import org.junit.Test
import rawhttp.cookies.persist.PeriodicFlushStrategy
import java.time.Duration
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
                return mock()
            }

            override fun submit(task: Runnable): Future<*> {
                task.run()
                return mock()
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
        strategy.onUpdate(mock())

        // AND run the flush action
        flushAction?.run()

        // THEN flush should be called
        count shouldBe 1
    }
}