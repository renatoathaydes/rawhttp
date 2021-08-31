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

        val cacheDurationMillis = 50L
        val threadCount = 10
        val runsPerThread = 4
        val sleepPerRun = 30L

        val dateHeaderProvider = DateHeaderProvider(Duration.ofMillis(cacheDurationMillis), dateHeaderMock)

        val dateHeaderValues = ConcurrentHashMap.newKeySet<String>()
        val threadIds = mutableSetOf<String>()

        val latch = CountDownLatch(threadCount)

        // Run m Threads, and on each Thread, get the Date header n times, sleeping t ms between each call
        for (t in 0 until threadCount) {
            Thread {
                for (r in 1 until runsPerThread) {
                    if (r > 1) sleep(sleepPerRun)
                    dateHeaderValues += dateHeaderProvider.get()["Date"]!!.first()
                }
                latch.countDown()
            }.apply { threadIds += id.toString() }.start()
        }

        latch.await(500, TimeUnit.MILLISECONDS) shouldBe true

        // as we set the Date values to each Thread ID
        dateHeaderValues shouldBe threadIds

        // each of the 10 Threads tries to get a header every 30ms. The cache for headers lasts for 50ms,
        // and it's thread-local so each Thread gets its own version, so they should get a cached one every second try.
        // Because each Thread runs 4 times, each Thread should cause 2 headers to be created...
        // In practice, it can happen that a Thread gets so delayed that it gets 3 or 4 headers, but we expect that
        // to happen only a few times... but it does happen a lot on GitHub Actions, so let's be satisfied as long
        // as not ALL requests were so late that none came from the cache :O
        val expectedNewHeaders = threadCount * 2

        // at least some requests should come from the cache
        val maxExpectedNewHeaders = (threadCount * 4) - 10

        // a few extra calls to the factory method are allowed because we don't synchronize
        createDateHeaderCounter.get() shouldBe between(expectedNewHeaders, maxExpectedNewHeaders)
    }

}
