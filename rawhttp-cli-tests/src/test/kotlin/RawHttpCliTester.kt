import org.hamcrest.CoreMatchers.equalTo
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.BeforeClass
import rawhttp.core.EagerHttpResponse
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpResponse
import rawhttp.core.client.TcpRawHttpClient
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.Thread.sleep
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

data class ProcessHandle(val process: Process,
                         private val outputStream: ByteArrayOutputStream,
                         private val errStream: ByteArrayOutputStream) {

    fun waitForEndAndGetStatus(): Int {
        val completed = process.waitFor(2, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            fail("Process not completed within the timeout:\n$this")
        }
        return process.exitValue()
    }

    val out: String by lazy(LazyThreadSafetyMode.NONE) {
        waitForEndAndGetStatus()
        outputStream.toString()
    }

    val err: String by lazy(LazyThreadSafetyMode.NONE) {
        waitForEndAndGetStatus()
        errStream.toString()
    }

}

abstract class RawHttpCliTester {

    companion object {
        private val CLI_EXECUTABLE: Array<String>

        const val SUCCESS_HTTP_REQUEST = "GET /saysomething HTTP/1.1\r\n" +
                "Host: localhost:8083\r\n" +
                "Accept: */*\r\n" +
                "User-Agent: RawHTTP"

        private const val SUCCESS_LOGGED_HTTP_REQUEST = "GET /saysomething HTTP/1.1\r\n" +
                "Host: localhost:8083\r\n" +
                "Accept: */*\r\n" +
                "User-Agent: RawHTTP"

        const val NOT_FOUND_HTTP_REQUEST = "GET /does/not/exist HTTP/1.1\r\n" +
                "Host: localhost:8083\r\n" +
                "Accept: */*\r\n" +
                "User-Agent: RawHTTP"

        private const val SUCCESS_HTTP_RESPONSE = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 9\r\n" +
                "\r\n" +
                "something"

        private val NOT_FOUND_HTTP_RESPONSE = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 18\r\n" +
                "\r\n" +
                "Resource Not Found".trimIndent()

        private var httpServerThread: Thread? = null

        init {
            val rawhttpCliJar = tryLocateRawHttpCliJar()

            val javaHome = System.getProperty("java.home")

            val cliJar = File(rawhttpCliJar)
            if (!cliJar.isFile) {
                throw IllegalStateException("The CLI launcher does not exist: $cliJar")
            }

            val java = File(javaHome, "bin/java")
            if (!java.canExecute()) {
                throw IllegalStateException("Cannot execute java: $java")
            }

            CLI_EXECUTABLE = arrayOf(java.absolutePath, "-jar", cliJar.absolutePath)

            println("Running tests with executable: ${CLI_EXECUTABLE.joinToString(" ")}")
        }

        private fun tryLocateRawHttpCliJar(): String {
            val guessLocation: () -> String? = {
                val workingDir = File(System.getProperty("user.dir"))
                listOf(".", "..")
                        .map { File(workingDir, "$it/rawhttp-cli/build/libs/rawhttp.jar").canonicalFile }
                        .filter { it.isFile }
                        .map { it.absolutePath }
                        .firstOrNull()
            }

            return System.getProperty("rawhttp.cli.jar")
                    ?: guessLocation()
                    ?: throw IllegalStateException("rawhttp.cli.jar system property must be set")
        }

        @BeforeClass
        @JvmStatic
        fun startHttpServer() {
            val http = RawHttp()
            val server = ServerSocket(8083)

            httpServerThread = Thread {
                try {
                    while (true) {
                        val client = server.accept()
                        println("Accepted connection from client $client")
                        try {
                            val request = http.parseRequest(client.getInputStream())

                            println("Received Request:\n$request")

                            if (request.uri.path == "/saysomething") {
                                http.parseResponse(SUCCESS_HTTP_RESPONSE)
                                        .writeTo(client.getOutputStream())
                            } else {
                                http.parseResponse(NOT_FOUND_HTTP_RESPONSE).writeTo(client.getOutputStream())
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        } finally {
                            client.close()
                        }
                    }
                } catch (e: InterruptedException) {
                    println("RawHttpCliTest HTTP Server stopped")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }.apply { start() }
        }

        @AfterClass
        @JvmStatic
        fun stopHttpServer() {
            httpServerThread!!.interrupt()
        }

        fun runCli(vararg args: String): ProcessHandle {
            val process = ProcessBuilder().command(*CLI_EXECUTABLE, *args).start()
            val outputStream = ByteArrayOutputStream(1024)
            val errStream = ByteArrayOutputStream(1024)

            Thread {
                process.inputStream.copyTo(outputStream)
            }.start()
            Thread {
                process.errorStream.copyTo(errStream)
            }.start()

            return ProcessHandle(process, outputStream, errStream)
        }

        fun assertOutputIsSuccessResponse(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo(SUCCESS_HTTP_RESPONSE))
            assertNoSysErrOutput(handle)
        }

        fun assertOutputIsSuccessResponseAndThenStatistics(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            val separator = "\n---------------------------------\n"
            val separatorIndex = handle.out.indexOf(separator)
            assertTrue("Expected to find separator in output:\n${handle.out}", separatorIndex > 0)
            assertThat(handle.out.substring(0 until separatorIndex), equalTo(SUCCESS_HTTP_RESPONSE))
            assertStatistics(handle.out.substring(separatorIndex + separator.length))
            assertNoSysErrOutput(handle)
        }

        fun assertOutputIs404Response(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo(NOT_FOUND_HTTP_RESPONSE))
            assertNoSysErrOutput(handle)
        }

        fun assertSuccessRequestIsLoggedThenSuccessResponse(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)

            // there should be a new-line between the request and the response,
            // plus 2 new-lines to indicate the end of the request
            assertThat(handle.out, equalTo(SUCCESS_LOGGED_HTTP_REQUEST + "\r\n\r\n\n" + SUCCESS_HTTP_RESPONSE))
            assertNoSysErrOutput(handle)
        }

        fun assertSuccessResponseStatus(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo(SUCCESS_HTTP_RESPONSE.substring(0..16)))
            assertNoSysErrOutput(handle)
        }

        fun assertSuccessResponseStats(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertStatistics(handle.out)
            assertNoSysErrOutput(handle)
        }

        private fun assertStatistics(output: String) {
            output.lines().run {
                assertThat(size, equalTo(6))
                assertTrue("Expected 'Connect time', got " + get(0),
                        get(0).matches(Regex("Connect time: \\d+\\.\\d{2} ms")))
                assertTrue("Expected 'First received byte time', got " + get(1),
                        get(1).matches(Regex("First received byte time: \\d+\\.\\d{2} ms")))
                assertTrue("Expected 'Total response time', got " + get(2),
                        get(2).matches(Regex("Total response time: \\d+\\.\\d{2} ms")))
                assertTrue("Expected 'Total response time', got " + get(3),
                        get(3).matches(Regex("Bytes received: \\d+")))
                assertTrue("Expected 'Throughput (bytes/sec)', got " + get(4),
                        get(4).matches(Regex("Throughput \\(bytes/sec\\): \\d+")))
                assertEquals(get(5), "")
            }
        }

        fun assertSuccessResponseBody(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo("something"))
            assertNoSysErrOutput(handle)
        }

        fun assertNoSysErrOutput(handle: ProcessHandle) {
            var errOut = handle.err
            if (errOut.startsWith("Picked up _JAVA_OPTIONS")) {
                errOut = errOut.lines().drop(1).joinToString("\n")
            }
            assertThat(errOut, equalTo(""))
        }

        fun sendHttpRequest(request: String): RawHttpResponse<*> {
            var response: EagerHttpResponse<*>? = null
            var failedConnectionAttempts = 0
            while (failedConnectionAttempts < 10) {
                try {
                    response = TcpRawHttpClient().use { client ->
                        client.send(RawHttp().parseRequest(request)).eagerly(false)
                    }
                    break
                } catch (e: IOException) {
                    failedConnectionAttempts++
                    println("Connection to server failed, retry attempt number $failedConnectionAttempts")
                    sleep(150)
                }
            }

            return response
                    ?: throw AssertionError("Unable to connect to server after $failedConnectionAttempts failed attempts")
        }

        fun ProcessHandle.verifyProcessTerminatedWithExitCode(expectedExitCode: Int) {
            val statusCode = waitForEndAndGetStatus()
            if (statusCode != expectedExitCode) {
                println("Process sysout:\n$out")
                println("Process syserr:\n$err")
                throw AssertionError("Expected process to exit with code $expectedExitCode but was $statusCode")
            }
        }

        fun ProcessHandle.sendStopSignalToRawHttpServer() {
            // the tests expect all output to be flush from the process,
            // so we need to wait a little bit before killing the server otherwise
            // process output may be missing
            sleep(250L)
            try {
                process.destroy()
            } catch (e: IOException) {
                // server probably died early
            }
        }

    }

}

private fun String.matches(regex: Regex): Boolean {
    return regex.matchEntire(this) != null
}
