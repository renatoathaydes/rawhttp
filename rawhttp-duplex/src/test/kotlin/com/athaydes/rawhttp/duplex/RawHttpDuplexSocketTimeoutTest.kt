package com.athaydes.rawhttp.duplex

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import rawhttp.core.RawHttp
import rawhttp.core.RawHttp.waitForPortToBeTaken
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.RawHttpServer
import rawhttp.core.server.TcpRawHttpServer
import java.net.SocketTimeoutException
import java.time.Duration
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class RawHttpDuplexSocketTimeoutTest {
    val duplex = RawHttpDuplex(
        RawHttpDuplexOptions.newBuilder()
            .withPingPeriod(Duration.ofMillis(50L))
            .build()
    )
    val server: RawHttpServer
    val serverMessages = ConcurrentLinkedQueue<Any>()

    init {
        server = startServer()
    }

    @AfterEach
    fun cleanup() {
        server.stop()
    }

    @Test
    @Timeout(4, unit = TimeUnit.SECONDS)
    fun shouldTimeoutWithoutPing() {
        val client = TcpRawHttpClient(DuplexClientOptions().apply { socketTimeout = 10 })
        val shortTimeoutDuplex = RawHttpDuplex(client)
        shouldThrow<SocketTimeoutException> {
            val errorQueue = LinkedBlockingDeque<Throwable>(1)
            shortTimeoutDuplex.connect(RawHttp().parseRequest("POST http://localhost:$port/duplex")) {
                object : MessageHandler {
                    override fun onError(error: Throwable) {
                        errorQueue.push(error)
                    }
                }
            }
            val throwable = errorQueue.poll(500, TimeUnit.MILLISECONDS)
            if (throwable != null) throw throwable
        }

        serverMessages shouldHaveSize 0

        client.close()
    }

    @Test
    @Timeout(4, unit = TimeUnit.SECONDS)
    fun shouldNotTimeoutWithPing() {
        val client = TcpRawHttpClient(DuplexClientOptions().apply { socketTimeout = 250 })
        // duplex will ping every 50ms
        val shortTimeoutDuplex = RawHttpDuplex(client, Duration.ofMillis(50L))
        val errorQueue = LinkedBlockingDeque<Throwable>(1)
        // will release the latch after at least 600ms,
        // then send a "hello" message to confirm the socket is ok
        val latch = CountDownLatch(1)

        shortTimeoutDuplex.connect(RawHttp().parseRequest("POST http://localhost:$port/duplex")) { sender ->
            Thread {
                Thread.sleep(600L)
                sender.sendTextMessage("hello")
                latch.countDown()
            }.start()
            object : MessageHandler {
                override fun onError(error: Throwable) {
                    errorQueue.push(error)
                }
            }
        }

        assert(latch.await(2, TimeUnit.SECONDS))

        val throwable = errorQueue.poll(100, TimeUnit.MILLISECONDS)
        if (throwable != null) throw throwable

        serverMessages shouldContainExactly listOf("hello")

        client.close()
    }

    private fun startServer(): RawHttpServer {
        val server = TcpRawHttpServer(port)
        server.start { request ->
            // whatever request comes in, we start a duplex connection
            Optional.of(duplex.accept(request) {
                object : MessageHandler {
                    override fun onTextMessage(message: String) {
                        serverMessages.add(message)
                    }

                    override fun onBinaryMessage(message: ByteArray) {
                        serverMessages.add(message)
                    }
                }
            })
        }
        waitForPortToBeTaken(port, Duration.ofSeconds(5))
        return server
    }
}