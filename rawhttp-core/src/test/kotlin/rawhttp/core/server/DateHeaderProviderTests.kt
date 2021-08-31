package rawhttp.core.server

import io.kotest.matchers.ints.between
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldHave
import org.junit.jupiter.api.Test
import rawhttp.core.RawHttpHeaders
import rawhttp.core.validDateHeader
import java.lang.Thread.sleep
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class DateHeaderProviderTests {

    @Test
    fun `Should be able to get a header once`() {
        val dateHeaderContainer = DateHeaderProvider(Duration.ofMillis(100)).get()
        dateHeaderContainer.asMap().size shouldBe 1
        dateHeaderContainer shouldHave validDateHeader()
    }

    @Test
    fun `Should cache the Date Header for the requested Duration`() {
        val createDateHeaderCounter = AtomicInteger()
        val dateHeaderMock = Supplier<RawHttpHeaders> {
            createDateHeaderCounter.incrementAndGet()
            RawHttpHeaders.newBuilder().with("Date", Thread.currentThread().id.toString()).build()
        }

        val cacheDurationMillis = 40L
        val threadCount = 10
        val repeatRunsPerThread = 4
        val sleepPerRun = 25L

        val dateHeaderProvider = DateHeaderProvider(Duration.ofMillis(cacheDurationMillis), dateHeaderMock)

        val dateHeaderValues = ConcurrentHashMap.newKeySet<String>()
        val threadIds = mutableSetOf<String>()

        val latch = CountDownLatch(threadCount)

        // Run m Threads, and on each Thread, get the Date header n times, sleeping t ms between each call
        for (t in 0 until threadCount) {
            Thread {
                for (r in 0 until repeatRunsPerThread) {
                    if (r > 0) sleep(sleepPerRun)
                    dateHeaderValues += dateHeaderProvider.get()["Date"]!!.first()
                }
                latch.countDown()
            }.apply { threadIds += id.toString() }.start()
        }

        latch.await(500, TimeUnit.MILLISECONDS) shouldBe true

        // as we set the Date values to each Thread ID
        dateHeaderValues shouldBe threadIds

        // the test runs for at least totalTime = (repeatRunsPerThread * sleepPerRun) ms.
        // so the number of times the factory method should be called is:
        // expectedFactoryCalls = threadCount * (totalTime / cacheDurationMillis)
        val totalTime = repeatRunsPerThread * sleepPerRun
        val expectedFactoryCalls = threadCount * (totalTime / cacheDurationMillis)

        // a few extra calls to the factory method are allowed because we don't synchronize
        createDateHeaderCounter.get() shouldBe between(expectedFactoryCalls.toInt(), expectedFactoryCalls.toInt() + 5)
    }

}
