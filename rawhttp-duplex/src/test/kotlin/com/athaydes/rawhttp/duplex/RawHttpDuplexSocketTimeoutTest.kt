package com.athaydes.rawhttp.duplex

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldThrow
import org.junit.After
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.server.RawHttpServer
import rawhttp.core.server.TcpRawHttpServer
import java.net.SocketTimeoutException
import java.util.Optional
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.TimeUnit

class RawHttpDuplexSocketTimeoutTest {
    val duplex = RawHttpDuplex()
    val server: RawHttpServer
    val serverMessages = ConcurrentLinkedQueue<Any>()

    init {
        server = startServer()
    }

    @After
    fun cleanup() {
        server.stop()
    }

    @Test(timeout = 1500L)
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

        serverMessages should beEmpty()
    }

    @Test(timeout = 1500L)
    fun shouldNotTimeoutWithPing() {
        val scheduler = Executors.newSingleThreadScheduledExecutor()
        val shortTimeoutDuplex = RawHttpDuplex(TcpRawHttpClient(DuplexClientOptions().apply { socketTimeout = 150 }))
        val errorQueue = LinkedBlockingDeque<Throwable>(1)

        shortTimeoutDuplex.connect(RawHttp().parseRequest("POST http://localhost:$port/duplex")) { sender ->
            var counter = 3
            scheduler.scheduleAtFixedRate({
                sender.ping()
                counter--
                if (counter == 0) {
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

        serverMessages should beEmpty()
    }

    private fun startServer(): RawHttpServer {
        val server = TcpRawHttpServer(port)
        server.start { request ->
            println("Accepting request\n$request")
            // whatever request comes in, we start a duplex connection
            Optional.of(duplex.accept(request) { _ ->
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
        Thread.sleep(250L)
        return server
    }
}