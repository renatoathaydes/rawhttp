package com.athaydes.rawhttp.duplex

import io.kotlintest.matchers.shouldBe
import org.junit.After
import org.junit.Test
import rawhttp.core.RawHttp
import rawhttp.core.server.RawHttpServer
import rawhttp.core.server.TcpRawHttpServer
import java.util.Arrays
import java.util.Optional
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val port = 8084

class RawHttpDuplexIntegrationTest {

    val duplex = RawHttpDuplex()
    val server: RawHttpServer

    init {
        server = startEchoDuplexServer()
    }

    @After
    fun cleanup() {
        server.stop()
    }

    @Test
    fun canCommunicateWithServerDuplex() {
        val receivedTextMessages = ArrayList<String>()
        val receivedBinaryMessages = ArrayList<ByteArray>()
        val latch = CountDownLatch(2)

        duplex.connect(RawHttp().parseRequest("POST http://localhost:$port/duplex"), { sender ->
            object : MessageHandler {
                init {
                    // immediately send a text message to the server
                    sender.sendTextMessage("Hello server, how are you?")
                }

                override fun onTextMessage(message: String) {
                    receivedTextMessages.add(message)

                    // once the echo-ed text message is received, send a binary message
                    sender.sendBinaryMessage(byteArrayOf(20, 30, 40, 50))
                    latch.countDown()
                }

                override fun onBinaryMessage(message: ByteArray) {
                    receivedBinaryMessages.add(message)

                    // will close the sender once the a binary message is received
                    sender.close()
                    latch.countDown()
                }
            }
        })

        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw AssertionError("Messages not received within the timeout")
        }

        receivedBinaryMessages.size shouldBe 1
        Arrays.toString(receivedBinaryMessages[0]) shouldBe Arrays.toString(byteArrayOf(20, 30, 40, 50, Byte.MIN_VALUE))
        receivedTextMessages shouldBe listOf("Message: Hello server, how are you?")

    }

    fun startEchoDuplexServer(): RawHttpServer {
        val server = TcpRawHttpServer(port)
        server.start { request ->
            println("Accepting request\n$request")
            // whatever request comes in, we start a duplex connection
            Optional.of(duplex.accept(request, { sender ->
                object : MessageHandler {
                    override fun onTextMessage(message: String) {
                        sender.sendTextMessage("Message: $message")
                    }

                    override fun onBinaryMessage(message: ByteArray) {
                        val response = message.copyOf(message.size + 1)
                        response[response.lastIndex] = Byte.MIN_VALUE
                        sender.sendBinaryMessage(response)
                    }
                }
            }))
        }
        Thread.sleep(250L)
        return server
    }
}