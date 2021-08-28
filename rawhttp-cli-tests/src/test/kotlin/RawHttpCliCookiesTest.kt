import RawHttpCliTester.Companion.verifyProcessTerminatedWithExitCode
import io.kotlintest.matchers.haveSize
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
import java.io.File
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
                "Location: http://localhost:8086/login\n" +
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
            val server = ServerSocket(8086)

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
                    val cookies = ServerCookieHelper.readClientCookies(request)
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
                            ServerCookieHelper.setCookie(
                                    http.parseResponse(SUCCESS_HTTP_RESPONSE),
                                    HttpCookie("sid", "foo").apply { maxAge = 120 })
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
            httpServerThread?.interrupt()
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
        assertHttpFileRanSuccessfully(handle)
    }

    @Test
    fun canRunHttpFileThatRequiresCookiesUsingEnvironmentAndEndingInTestFailure() {
        val basicHttpFile = RawHttpCliTester::class.java.getResource("/reqin-edit-tests/login/login.http").file

        val handle = runCli("run", basicHttpFile, "-e", "test")

        handle.verifyProcessTerminatedWithExitCode(5)

        val outLines = handle.out.lines()

        outLines should haveSize(21)

        outLines.subList(0, 9) shouldBe listOf("HTTP/1.1 302",
                "Location: http://localhost:8086/login",
                "Content-Length: 0",
                "",
                "HTTP/1.1 200 OK",
                "Content-Type: text/plain",
                "Content-Length: 28",
                "",
                "Send your credentials to me!")
        outLines[9] should match("TEST OK \\(\\d+ms\\): We are automatically redirected to the login page")
        outLines.subList(10, 14) shouldBe listOf("HTTP/1.1 401 Bad Credentials",
                "Content-Length: 0",
                "",
                "")
        outLines[14] should match("TEST OK \\(\\d+ms\\): We get the bad credentials response")
        outLines.subList(15, 19) shouldBe listOf("HTTP/1.1 401 Bad Credentials",
                "Content-Length: 0",
                "",
                "")
        outLines[19] should match("TEST FAILED \\(\\d+ms\\): We get the SID cookie")
        outLines[20] shouldBe ""

        handle.err.trim() shouldBe "expected 200 response, but status was 401$EOL" +
                "FAIL: There were test failures!"
    }

    @Test
    fun canRunHttpFileUsingPreLoadedCookies() {
        val basicHttpFile = RawHttpCliTester::class.java.getResource("/reqin-edit-tests/login/login.http").file
        val cookieJar = File("temp_cookie_jar")
        cookieJar.exists() shouldBe false
        cookieJar.deleteOnExit()

        val handle1 = runCli("run", basicHttpFile, "--cookiejar", cookieJar.absolutePath)

        handle1.verifyProcessTerminatedWithExitCode(0)

        assertHttpFileRanSuccessfully(handle1)
        cookieJar.exists() shouldBe true

        // run file again, this time re-using the cookies from the previous run
        val handle2 = runCli("run", basicHttpFile, "--cookiejar", cookieJar.absolutePath)

        handle2.verifyProcessTerminatedWithExitCode(5) // test failure

        val outLines = handle2.out.lines()

        // the client went straight to the Hello User response because it already had a persisted cookie
        outLines.subList(0, 5) shouldBe listOf("HTTP/1.1 200 OK",
                "Content-Type: text/plain",
                "Content-Length: 10",
                "",
                "Hello user")

        handle2.err.trim() shouldBe """
            expected to go to login page, got response: Hello user
            FAIL: There were test failures!
        """.trimIndent().trimEnd()
    }

    private fun assertHttpFileRanSuccessfully(handle: ProcessHandle) {
        val outLines = handle.out.lines()

        outLines should haveSize(29)

        outLines.subList(0, 9) shouldBe listOf("HTTP/1.1 302",
                "Location: http://localhost:8086/login",
                "Content-Length: 0",
                "",
                "HTTP/1.1 200 OK",
                "Content-Type: text/plain",
                "Content-Length: 28",
                "",
                "Send your credentials to me!")
        outLines[9] should match("TEST OK \\(\\d+ms\\): We are automatically redirected to the login page")
        outLines.subList(10, 14) shouldBe listOf("HTTP/1.1 401 Bad Credentials",
                "Content-Length: 0",
                "",
                "")
        outLines[14] should match("TEST OK \\(\\d+ms\\): We get the bad credentials response")
        outLines.subList(15, 21) shouldBe listOf("HTTP/1.1 200 OK",
                "Content-Type: text/plain",
                "Content-Length: 9",
                "Set-Cookie: sid=\"foo\"; Max-Age=120",
                "",
                "something")
        outLines[21] should match("TEST OK \\(\\d+ms\\): We get the SID cookie")
        outLines.subList(22, 27) shouldBe listOf("HTTP/1.1 200 OK",
                "Content-Type: text/plain",
                "Content-Length: 10",
                "",
                "Hello user")
        outLines[27] should match("TEST OK \\(\\d+ms\\): With the login cookie, we can get what we wanted")
        outLines[28] shouldBe ""

        handle.err shouldBe ""
    }

}