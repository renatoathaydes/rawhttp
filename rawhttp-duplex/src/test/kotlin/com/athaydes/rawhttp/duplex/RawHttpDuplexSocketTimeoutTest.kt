package com.athaydes.rawhttp.duplex

import io.kotest.assertions.throwables.shouldThrow
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
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class RawHttpDuplexSocketTimeoutTest {
    val duplex = RawHttpDuplex()
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
    @Timeout(2, unit = TimeUnit.SECONDS)
    fun shouldTimeoutWithoutPing() {
        val shortTimeoutDuplex = RawHttpDuplex(TcpRawHttpClient(DuplexClientOptions().apply { socketTimeout = 50 }))
        shouldThrow<SocketTimeoutException> {
            val errorQueue = LinkedBlockingDeque<Throwable>(1)
            shortTimeoutDuplex.connect(RawHttp().parseRequest("POST http://localhost:$port/duplex")) { _ ->
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
    }

    @Test
    @Timeout(2, unit = TimeUnit.SECONDS)
    fun shouldNotTimeoutWithPing() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val shortTimeoutDuplex = RawHttpDuplex(TcpRawHttpClient(DuplexClientOptions().apply { socketTimeout = 250 }))
        val errorQueue = LinkedBlockingDeque<Throwable>(1)

        shortTimeoutDuplex.connect(RawHttp().parseRequest("POST http://localhost:$port/duplex")) { sender ->
            val counter = AtomicInteger(3)
            scheduler.scheduleAtFixedRate({
                sender.ping()
                if (counter.decrementAndGet() == 0) {
                    sender.close()
                    scheduler.shutdown()
                }
            }, 50L, 50L, TimeUnit.MILLISECONDS)
            object : MessageHandler {
                override fun onError(error: Throwable) {
                    errorQueue.push(error)
                }
            }
        }

        val throwable = errorQueue.poll(500, TimeUnit.MILLISECONDS)
        if (throwable != null) throw throwable

        serverMessages shouldHaveSize 0
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