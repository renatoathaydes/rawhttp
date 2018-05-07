package com.athaydes.rawhttp.core.server

import com.athaydes.rawhttp.core.RawHttpHeaders
import io.kotlintest.matchers.beGreaterThan
import io.kotlintest.matchers.beLessThan
import io.kotlintest.matchers.between
import io.kotlintest.matchers.haveSize
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldNotBe
import io.kotlintest.specs.StringSpec
import java.lang.Thread.sleep
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.Supplier

class DateHeaderProviderTests : StringSpec({

    "Should be able to get a header once" {
        val dateHeaderContainer = DateHeaderProvider(Duration.ofMillis(100)).get()
        dateHeaderContainer.asMap().size shouldBe 1
        dateHeaderContainer["Date"] shouldNotBe null

        val dateHeaderValues = dateHeaderContainer["Date"]!!
        dateHeaderValues should haveSize(1)

        val dateHeaderValue = dateHeaderValues.first()
        val parsedDate = try {
            LocalDateTime.parse(dateHeaderValue, DateTimeFormatter.RFC_1123_DATE_TIME)
        } catch (e: DateTimeParseException) {
            throw AssertionError("Date header value is invalid: $dateHeaderValue", e)
        }

        val now = LocalDateTime.now(ZoneOffset.UTC)

        parsedDate should beLessThan(now)
        parsedDate should beGreaterThan(now.minusSeconds(5))
    }

    "Should cache the Date Header for the requested Duration" {
        val createDateHeaderCounter = AtomicInteger()
        val dateHeaderMock = Supplier<RawHttpHeaders> {
            createDateHeaderCounter.incrementAndGet()
            RawHttpHeaders.Builder.newBuilder().with("Date", Thread.currentThread().id.toString()).build()
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
        dateHeaderValues shouldEqual threadIds

        // the test runs for at least totalTime = (repeatRunsPerThread * sleepPerRun) ms.
        // so the number of times the factory method should be called is:
        // expectedFactoryCalls = threadCount * (totalTime / cacheDurationMillis)
        val totalTime = repeatRunsPerThread * sleepPerRun
        val expectedFactoryCalls = threadCount * (totalTime / cacheDurationMillis)

        // a few extra calls to the factory method are allowed because we don't synchronize
        createDateHeaderCounter.get() shouldBe between(expectedFactoryCalls.toInt(), expectedFactoryCalls.toInt() + 5)
    }

})
