package com.athaydes.rawhttp.duplex

import http
import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.haveSize
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import org.junit.Test
import rawhttp.core.RawHttpHeaders
import java.util.ArrayList
import java.util.Arrays.asList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.stream.Stream

class RawHttpDuplexTest {

    private val duplex = RawHttpDuplex()

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

        firstChunk.extensions.getFirst("Content-Type").orElse("NOT FOUND") shouldBe "text/plain"
        firstChunk.extensions.getFirst("Charset").orElse("NOT FOUND") shouldBe "UTF-8"
        java.lang.String(firstChunk.data, Charsets.UTF_8) shouldBe "hello duplex"

        lastChunk.extensions.headerNames should beEmpty()
        java.lang.String(lastChunk.data) shouldBe ""
    }

    @Test
    fun canReadAndWriteBinaryMessage() {
        val requestWithBinaryMessagesInBody = http.parseRequest("""
            POST / HTTP/1.1
            Host: example.com
            Transfer-Encoding: chunked

            5
            12345
            2;something=true;other=false
            21
            0

            """.trimIndent())

        val textMessages = ArrayList<String>()
        val binaryMessages = ArrayList<ByteArray>()
        val extensionsList = ArrayList<RawHttpHeaders>()
        val errors = ArrayList<Throwable>()
        val closeLatch = CountDownLatch(1)

        val response = duplex.accept(requestWithBinaryMessagesInBody) { sender ->
            object : MessageHandler {
                init {
                    sender.sendBinaryMessage(byteArrayOf(10, 9, 8, 7, 6, 5))
                }

                override fun onBinaryMessage(message: ByteArray, extensions: RawHttpHeaders) {
                    binaryMessages.add(message)
                    extensionsList.add(extensions)
                }

                override fun onTextMessage(message: String, extensions: RawHttpHeaders) {
                    textMessages.add(message)
                    extensionsList.add(extensions)
                }

                override fun onError(error: Throwable) {
                    errors.add(error)
                }

                override fun onClose() {
                    closeLatch.countDown()
                }

            }
        }

        if (!closeLatch.await(5, TimeUnit.SECONDS)) {
            throw AssertionError("Did not close handler within timeout")
        }

        textMessages should beEmpty()
        binaryMessages.map { it.asList() } shouldBe listOf(
                listOf<Byte>(49, 50, 51, 52, 53), listOf<Byte>(50, 49))
        errors should beEmpty()

        extensionsList.size shouldBe 2
        extensionsList[0].headerNames should beEmpty()
        extensionsList[1].headerNames shouldBe listOf("something", "other")
        extensionsList[1].asMap() shouldBe mapOf(
                "SOMETHING" to listOf("true"),
                "OTHER" to listOf("false"))

        val responseBody = response.body.orElseThrow { AssertionError("Response should have body") }

        responseBody.isChunked shouldBe true
        val chunkedResponseBody = responseBody.asChunkedBodyContents()
                .orElseThrow { AssertionError("Cannot get body contents") }

        chunkedResponseBody.chunks should haveSize(2)

        val firstChunk = chunkedResponseBody.chunks[0]
        val lastChunk = chunkedResponseBody.chunks[1]

        firstChunk.extensions.headerNames should beEmpty()
        firstChunk.data.asList() shouldBe listOf<Byte>(10, 9, 8, 7, 6, 5)

        lastChunk.extensions.headerNames should beEmpty()
        java.lang.String(lastChunk.data) shouldBe ""
    }

}
