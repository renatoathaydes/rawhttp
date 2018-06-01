import com.athaydes.rawhttp.duplex.MessageHandler
import com.athaydes.rawhttp.duplex.MessageSender
import com.athaydes.rawhttp.duplex.RawHttpDuplex
import com.athaydes.rawhttp.duplex.port
import rawhttp.core.EagerHttpResponse
import rawhttp.core.RawHttp
import rawhttp.core.body.StringBody
import rawhttp.core.server.TcpRawHttpServer
import java.io.IOException
import java.net.URLEncoder
import java.util.Optional
import java.util.Scanner
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

val http = RawHttp()
val duplex = RawHttpDuplex()

object ChatServer {
    val clientByName = mutableMapOf<String, ChatHandler>()

    class ChatHandler(val name: String, val sender: MessageSender) : MessageHandler {
        init {
            clientByName.put(name, this)
            tellOthers("$name joined...")
        }

        override fun onTextMessage(message: String) {
            println("$name says: $message")
            tellOthers("$name: $message")
        }

        override fun onClose() {
            tellOthers("$name left!")
            clientByName.remove(name)
        }

        private fun tellOthers(message: String) {
            clientByName.values.forEach {
                if (this != it) {
                    it.sender.sendTextMessage(message)
                }
            }
        }
    }

    fun runServer() {
        val server = TcpRawHttpServer(port)
        server.start { request ->
            println(request)
            if (request.method == "POST") {
                val name = request.uri.path.substring(1)
                return@start if (name.isEmpty() || name.contains("/")) {
                    Optional.of(Responses.notFound)
                } else {
                    println("Accepting $name to chatroom")
                    Optional.of(duplex.accept(request, { sender ->
                        ChatHandler(name, sender)
                    }))
                }
            } else {
                Optional.of(Responses.methodNotAllowed)
            }
        }
    }

    object Responses {
        val methodNotAllowed: EagerHttpResponse<Void> = http.parseResponse(
                """HTTP/1.1 405 Method Not Allowed
               Allow: POST
            """.trimIndent()).eagerly()

        val notFound: EagerHttpResponse<Void> = http.parseResponse(
                """HTTP/1.1 404 Not Found
            """.trimIndent())
                .withBody(StringBody("Resource not found.\n" +
                        "To enter the chat, use 'POST /your-name'")).eagerly()
    }
}

object ChatClient {

    val scanner = Scanner(System.`in`)

    fun runClient() {
        print("Welcome! What's your name?\n> ")
        val name = URLEncoder.encode(scanner.nextLine(), "UTF-8")
        val running = AtomicBoolean(true)
        val executor = Executors.newSingleThreadExecutor()

        try {
            duplex.connect(http.parseRequest("POST http://localhost:$port/$name"), { sender ->
                object : MessageHandler, Runnable {

                    init {
                        executor.submit(this)
                    }

                    override fun run() {
                        while (running.get()) {
                            prompt()
                            val message = scanner.nextLine()
                            if (message == "quit") {
                                sender.close()
                                println("Goodbye!")
                                running.set(false)
                            } else {
                                sender.sendTextMessage(message)
                            }
                        }
                    }

                    fun prompt() {
                        print("$name: ")
                    }

                    override fun onTextMessage(message: String) {
                        println(message)
                        prompt()
                    }

                    override fun onClose() {
                        running.set(false)
                        executor.shutdown()
                    }
                }
            })
        } catch (e: IOException) {
            e.printStackTrace()
            running.set(false)
            executor.shutdownNow()
        }
    }

}

fun main(args: Array<String>) {
    val serverMode = args.size == 1 && args[0] == "server"
    if (args.size > 1 || args.size == 1 && args[0] !in setOf("client", "server")) {
        System.err.println("One argument is acceptable: 'server' or 'client'. 'client' is the default")
        System.exit(1)
    }

    if (serverMode) {
        ChatServer.runServer()
    } else {
        ChatClient.runClient()
    }
}
