package com.athaydes.rawhttp.duplex

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.haveSize
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import org.junit.Test
import java.util.ArrayList
import java.util.Arrays.asList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class RawHttpDuplexTest {

    val duplex = RawHttpDuplex()

    @Test
    fun canReadAndWriteTextMessage() {
        val textMessages = ArrayList<String>()
        val binaryMessages = ArrayList<ByteArray>()
        val errors = ArrayList<Throwable>()
        val closeLatch = CountDownLatch(1)

        val response = duplex.acceptText(Stream.of("received message", ""), { sender ->
            object : MessageHandler {
                init {
                    sender.sendTextMessage("hello duplex")
                }

                override fun onBinaryMessage(message: ByteArray) {
                    binaryMessages.add(message)
                }

                override fun onTextMessage(message: String) {
                    textMessages.add(message)
                }

                override fun onError(error: Throwable) {
                    errors.add(error)
                }

                override fun onClose() {
                    closeLatch.countDown()
                }

            }
        })

        if (!closeLatch.await(5, TimeUnit.SECONDS)) {
            throw AssertionError("Did not close handler within timeout")
        }

        textMessages shouldBe asList("received message")
        binaryMessages should beEmpty()
        errors should beEmpty()

        val responseBody = response.body.orElseThrow { AssertionError("Response should have body") }

        responseBody.isChunked shouldBe true
        val chunkedResponseBody = responseBody.asChunkedBodyContents()
                .orElseThrow { AssertionError("Cannot get body contents") }

        chunkedResponseBody.chunks should haveSize(2)

        val firstChunk = chunkedResponseBody.chunks[0]
        val lastChunk = chunkedResponseBody.chunks[1]

        java.lang.String(firstChunk.data) shouldBe "hello duplex"
        java.lang.String(lastChunk.data) shouldBe ""
    }
}
