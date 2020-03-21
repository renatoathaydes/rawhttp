import RawHttpCliTester.Companion.verifyProcessTerminatedWithExitCode
import io.kotlintest.matchers.match
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import rawhttp.cookies.ServerCookieHelper
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.errors.InvalidHttpRequest
import java.io.IOException
import java.net.HttpCookie
import java.net.ServerSocket

class RawHttpCliCookiesTest {

    companion object {
        private const val SUCCESS_HTTP_RESPONSE = "HTTP/1.1 200 OK\n" +
                "Content-Type: text/plain\n" +
                "Content-Length: 9\n" +
                "\n" +
                "something"

        private const val REDIRECT_TO_LOGIN_HTTP_RESPONSE = "HTTP/1.1 302\n" +
                "Location: http://localhost:8084/login\n" +
                "Content-Length: 0"

        private const val BAD_CREDENTIALS_RESPONSE = "HTTP/1.1 401 Bad Credentials\n" +
                "Content-Length: 0"

        private const val ERROR_HTTP_RESPONSE = "HTTP/1.1 500 Server Error\n" +
                "Content-Length: 0"

        val http = RawHttp()
        private var httpServerThread: Thread? = null

        @BeforeClass
        @JvmStatic
        fun startHttpServer() {
            val server = ServerSocket(8084)

            httpServerThread = Thread {
                try {
                    while (true) {
                        val client = server.accept()
                        println("Accepted connection from client $client")
                        Thread {
                            while (true) {
                                try {
                                    val request = http.parseRequest(client.getInputStream()).eagerly()
                                    println("Received Request:\n$request")
                                    route(request).writeTo(client.getOutputStream())
                                } catch (e: InvalidHttpRequest) {
                                    // likely EOF
                                    println("$e")
                                    break
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    break
                                }
                            }
                            println("Closing client")
                            client.close()
                        }.apply {
                            isDaemon = true
                            start()
                        }
                    }
                } catch (e: InterruptedException) {
                    println("RawHttpCliTest HTTP Server stopped")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.apply {
                isDaemon = true
                start()
            }
        }

        private fun route(request: RawHttpRequest): RawHttpResponse<Void> {
            return when (request.uri.path) {
                "/me" -> {
                    val cookies = ServerCookieHelper.readCookies(request)
                    val sid = cookies.find { it.name == "sid" }?.value ?: ""
                    if (sid.isBlank())
                        http.parseResponse(REDIRECT_TO_LOGIN_HTTP_RESPONSE)
                    else // we don't even check the SID value :D
                        http.parseResponse(SUCCESS_HTTP_RESPONSE)
                                .withBody(StringBody("Hello user"))
                }
                "/login" ->
                    when (request.method) {
                        "POST" -> (if (request.body.map { it.decodeBodyToString(Charsets.UTF_8) }
                                        .orElse("") == "my password is 123")
                            ServerCookieHelper.withCookie(
                                    http.parseResponse(SUCCESS_HTTP_RESPONSE),
                                    HttpCookie("sid", "foo"))
                        else http.parseResponse(BAD_CREDENTIALS_RESPONSE))
                        "GET" -> http.parseResponse(SUCCESS_HTTP_RESPONSE)
                                .withBody(StringBody("Send your credentials to me!"))
                        else -> http.parseResponse(ERROR_HTTP_RESPONSE)
                    }
                else ->
                    http.parseResponse(ERROR_HTTP_RESPONSE)
            }
        }

        @AfterClass
        @JvmStatic
        fun stopHttpServer() {
            httpServerThread!!.interrupt()
        }

        @JvmStatic
        fun main(args: Array<String>) {
            startHttpServer()
            System.`in`.read()
            stopHttpServer()
        }
    }

    @Test
    fun canRunHttpFileThatRequiresCookies() {
        val basicHttpFile = RawHttpCliTester::class.java.getResource("/reqin-edit-tests/login/login.http").file

        val handle = runCli("run", basicHttpFile)

        handle.verifyProcessTerminatedWithExitCode(0)

        val outLines = handle.out.lines()

        outLines[0] shouldBe "HTTP/1.1 302"
        outLines[1] shouldBe "Location: http://localhost:8084/login"
        outLines[2] shouldBe "Content-Length: 0"
        outLines[3] shouldBe ""
        outLines[4] shouldBe "HTTP/1.1 200 OK"
        outLines[5] shouldBe "Content-Type: text/plain"
        outLines[6] shouldBe "Content-Length: 28"
        outLines[7] shouldBe ""
        outLines[8] shouldBe "Send your credentials to me!"
        outLines[9] should match("TEST OK \\(\\d+ms\\): We are automatically redirected to the login page")
        outLines[10] shouldBe "HTTP/1.1 401 Bad Credentials"
        outLines[11] shouldBe "Content-Length: 0"
        outLines[12] shouldBe ""
        outLines[13] shouldBe ""
        outLines[14] should match("TEST OK \\(\\d+ms\\): We get the bad credentials response")
        outLines[15] shouldBe "HTTP/1.1 200 OK"
        outLines[16] shouldBe "Content-Type: text/plain"
        outLines[17] shouldBe "Content-Length: 9"
        outLines[18] shouldBe "Set-Cookie: sid=\"foo\""
        outLines[19] shouldBe ""
        outLines[20] shouldBe "something"
        // TODO more output to check

    }

}