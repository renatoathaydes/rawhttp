import org.hamcrest.CoreMatchers
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.not
import org.hamcrest.CoreMatchers.startsWith
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeAll
import rawhttp.core.EagerHttpResponse
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpResponse
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import rawhttp.core.errors.InvalidHttpRequest
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.lang.Thread.sleep
import java.net.ServerSocket
import java.nio.file.Paths
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME
import java.util.concurrent.TimeUnit

val IS_DEBUG = System.getProperty("rawhttp-cli-tester-debug") != null

val IS_WINDOWS = System.getProperty("os.name").lowercase().contains("windows")

val EOL = if (IS_WINDOWS) "\r\n" else "\n"

val replyResponseFile = File("response.txt")

object JavaImageCliRunner {
    val CLI_EXECUTABLE: Array<String>

    init {
        val javaImageDir = System.getProperty("rawhttp.cli.image")
        val imageDir = File(javaImageDir)
        if (!imageDir.isDirectory) {
            throw IllegalStateException("The CLI Java Image does not exist: $imageDir")
        }
        val cliExec = File(imageDir, if (IS_WINDOWS) "bin\\rawhttp.bat" else "bin/rawhttp")
        if (!cliExec.isFile) {
            throw IllegalStateException("The CLI executable inside the Java Image does not exist: $cliExec")
        }

        CLI_EXECUTABLE = arrayOf(cliExec.absolutePath)
    }
}

object FatJarCliRunner {
    val CLI_EXECUTABLE: Array<String>

    init {
        val rawhttpCliJar = tryLocateRawHttpCliJar()

        val javaHome = System.getProperty("java.home")

        val cliJar = File(rawhttpCliJar)
        if (!cliJar.isFile) {
            throw IllegalStateException("The CLI launcher does not exist: $cliJar")
        }

        val javaExec = if (IS_WINDOWS) "java.exe" else "java"

        val java = sequenceOf(
            Paths.get(javaHome, "bin", javaExec).toFile(),
            Paths.get(javaHome, "jre", "bin", javaExec).toFile()
        ).filter { it.isFile }.firstOrNull()
            ?: throw IllegalStateException(
                "Cannot locate the java command under " +
                        "JAVA_HOME/bin or JAVA_HOME/jre/bin.\n" +
                        "Contents of JAVA_HOME/bin: ${
                            Paths.get(javaHome, "bin").toFile().listFiles()?.joinToString { it.name }
                        }\n" +
                        "Contents of JAVA_HOME/jre/bin: ${
                            Paths.get(javaHome, "jre", "bin").toFile().listFiles()?.joinToString { it.name }
                        }"
            )

        if (!java.canExecute()) {
            throw IllegalStateException("Cannot execute java: $java")
        }

        CLI_EXECUTABLE = if (IS_DEBUG)
            arrayOf(
                java.absolutePath, "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=8000",
                "-jar", cliJar.absolutePath
            )
        else arrayOf(java.absolutePath, "-jar", cliJar.absolutePath)

        println(
            "Running tests with executable (${if (IS_DEBUG) "debug mode" else "no debug"}): " +
                    CLI_EXECUTABLE.joinToString(" ")
        )
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

}

data class ProcessHandle(
    val process: Process,
    private val outputStream: ByteArrayOutputStream,
    private val errStream: ByteArrayOutputStream
) {

    fun waitForEndAndGetStatus(): Int {
        val completed = process.waitFor(if (IS_DEBUG) 500 else 5, TimeUnit.SECONDS)

        if (!completed) {
            process.destroyForcibly()
            fail<Unit>("Process not completed within the timeout:\n$this")
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

sealed class CliRunner {
    abstract val command: Array<String>

    object FatJar : CliRunner() {
        override val command: Array<String>
            get() = FatJarCliRunner.CLI_EXECUTABLE
    }

    object JavaImage : CliRunner() {
        override val command: Array<String>
            get() = JavaImageCliRunner.CLI_EXECUTABLE
    }
}

abstract class RawHttpCliTester {

    abstract val cliRunner: CliRunner

    fun runCli(vararg args: String): ProcessHandle {
        val process = ProcessBuilder().command(*cliRunner.command, *args).start()
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

    companion object {

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

        private const val SUCCESS_GET_FOO_HTTP_RESPONSE = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 7\r\n" +
                "\r\n" +
                "GET foo"

        private const val SUCCESS_POST_FOO_HTTP_RESPONSE = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/plain"

        private val NOT_FOUND_HTTP_RESPONSE = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: 18\r\n" +
                "\r\n" +
                "Resource Not Found".trimIndent()

        private var httpServerThread: Thread? = null

        @BeforeAll
        @JvmStatic
        fun startHttpServer() {
            val http = RawHttp()
            val server = ServerSocket(8083)

            httpServerThread = Thread {
                try {
                    while (true) {
                        val client = server.accept()
                        println("Accepted connection from client $client")
                        Thread {
                            while (true) {
                                try {
                                    val request = http.parseRequest(client.getInputStream())

                                    println("Received Request:\n$request")

                                    when (request.uri.path) {
                                        "/saysomething" ->
                                            http.parseResponse(SUCCESS_HTTP_RESPONSE)
                                                .writeTo(client.getOutputStream())
                                        "/foo" ->
                                            when (request.method) {
                                                "GET" -> http.parseResponse(SUCCESS_GET_FOO_HTTP_RESPONSE)
                                                    .writeTo(client.getOutputStream())
                                                "POST" -> http.parseResponse(SUCCESS_POST_FOO_HTTP_RESPONSE)
                                                    .withBody(request.body.map {
                                                        StringBody(
                                                            it.decodeBodyToString(
                                                                Charsets.UTF_8
                                                            )
                                                        )
                                                    }
                                                        .orElse(null))
                                                    .writeTo(client.getOutputStream())
                                                else -> http.parseResponse(NOT_FOUND_HTTP_RESPONSE)
                                                    .writeTo(client.getOutputStream())
                                            }
                                        "/reply" -> http.parseResponse(SUCCESS_POST_FOO_HTTP_RESPONSE)
                                            .withBody(StringBody(request.body.map {
                                                "Received:" + it.decodeBodyToString(
                                                    Charsets.UTF_8
                                                )
                                            }
                                                .orElse("Did not receive anything")))
                                            .writeTo(client.getOutputStream())
                                        else ->
                                            http.parseResponse(NOT_FOUND_HTTP_RESPONSE)
                                                .writeTo(client.getOutputStream())
                                    }
                                } catch (e: InvalidHttpRequest) {
                                    // likely EOF
                                    break
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    break
                                }
                            }
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

        @AfterAll
        @JvmStatic
        fun stopHttpServer() {
            httpServerThread!!.interrupt()
        }

        fun assertOutputIsSuccessResponse(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo(SUCCESS_HTTP_RESPONSE + EOL))
            assertNoSysErrOutput(handle)
        }

        fun assertOutputIsSuccessResponseAndThenStatistics(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            val separator = "$EOL---------------------------------$EOL"
            val separatorIndex = handle.out.indexOf(separator)
            assertThat(
                "Expected to find separator in output:\n${handle.out}",
                separatorIndex, CoreMatchers.allOf(not(0), not(-1))
            )
            assertThat(handle.out.substring(0 until separatorIndex), equalTo(SUCCESS_HTTP_RESPONSE + EOL))
            assertStatistics(handle.out.substring(separatorIndex + separator.length))
            assertNoSysErrOutput(handle)
        }

        fun assertOutputIs404Response(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo(NOT_FOUND_HTTP_RESPONSE + EOL))
            assertNoSysErrOutput(handle)
        }

        fun assertSuccessRequestIsLoggedThenSuccessResponse(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)

            // there should be a new-line between the request and the response,
            // plus 2 new-lines to indicate the end of the request
            assertThat(
                handle.out, equalTo(
                    SUCCESS_LOGGED_HTTP_REQUEST + "\r\n\r\n" +
                            EOL + SUCCESS_HTTP_RESPONSE + EOL
                )
            )
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

        fun assertSuccessResponseReplyToFiles(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo("Received:This is the foo file$EOL"))
            assertNoSysErrOutput(handle)
        }

        fun assertReplyResponseStoredInFile() {
            assertTrue(replyResponseFile.exists())
            assertThat(
                replyResponseFile.readText(), equalTo(
                    "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/plain\r\n" +
                            "Content-Length: 29\r\n" +
                            "\r\n" +
                            "Received:This is the foo file"
                )
            )
        }

        fun assertGetFooThenPostFooRequestsAndStats(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)

            val getFooRequest = "GET /foo HTTP/1.1\r\nAccept: */*\r\nHost: localhost\r\n\r\n$EOL"
            assertThat(handle.out, startsWith(getFooRequest))

            var out = handle.out.substring(getFooRequest.length)
            val secondRequestIndex = out.indexOf("POST")
            assertTrue(secondRequestIndex > 0)
            val firstStats = out.substring(0, secondRequestIndex)
            assertStatistics(firstStats)

            val postFooRequest = "POST /foo HTTP/1.1\r\nContent-Type: application/json\r\n" +
                    "Host: localhost\r\nContent-Length: 12\r\n\r\n{prod: true}$EOL"
            out = out.substring(secondRequestIndex)
            assertThat(out, startsWith(postFooRequest))

            out = out.substring(postFooRequest.length)
            assertStatistics(out)

            assertNoSysErrOutput(handle)
        }

        fun assertGetFooResponseThenPostFooResponse(handle: ProcessHandle, postFooBody: String) {
            val postResponse = SUCCESS_POST_FOO_HTTP_RESPONSE +
                    "\r\nContent-Length: ${postFooBody.length}" +
                    "\r\n\r\n$postFooBody$EOL"

            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo(SUCCESS_GET_FOO_HTTP_RESPONSE + EOL + postResponse))
            assertNoSysErrOutput(handle)
        }

        private fun assertStatistics(output: String) {
            output.lines().run {
                assertThat("Expected 6 element, got: " + toString(), size, equalTo(6))
                assertTrue(
                    get(0).matches(Regex("Connect time: \\d+\\.\\d{2} ms")),
                    "Expected 'Connect time', got " + get(0)
                )
                assertTrue(
                    get(1).matches(Regex("First received byte time: \\d+\\.\\d{2} ms")),
                    "Expected 'First received byte time', got " + get(1)
                )
                assertTrue(
                    get(2).matches(Regex("Total response time: \\d+\\.\\d{2} ms")),
                    "Expected 'Total response time', got " + get(2)
                )
                assertTrue(
                    get(3).matches(Regex("Bytes received: \\d+")),
                    "Expected 'Total response time', got " + get(3)
                )
                assertTrue(
                    get(4).matches(Regex("Throughput \\(bytes/sec\\): \\d+")),
                    "Expected 'Throughput (bytes/sec)', got " + get(4)
                )
                assertThat(get(5), equalTo(""))
            }
        }

        fun assertSuccessResponseBody(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            assertThat(handle.out, equalTo("something$EOL"))
            assertNoSysErrOutput(handle)
        }

        fun assertHttpTestResults(handle: ProcessHandle) {
            handle.verifyProcessTerminatedWithExitCode(0)
            handle.out.lines().run {
                assertThat(size, equalTo(6))
                assertThat(get(0), equalTo("HTTP/1.1 200 OK"))
                assertThat(get(1), get(1).matches(Regex("TEST OK \\(\\d+ms\\): response is 200")))
                assertThat(get(2), equalTo("HTTP/1.1 200 OK"))
                assertThat(get(3), get(3).matches(Regex("TEST OK \\(\\d+ms\\): response again is 200")))
                assertThat(get(4), get(4).matches(Regex("TEST OK \\(\\d+ms\\): body is as expected")))
                assertThat(get(5), equalTo(""))
            }

            assertNoSysErrOutput(handle)
        }

        fun assertNoSysErrOutput(handle: ProcessHandle) {
            assertSysErrOutput(handle, "")
        }

        fun assertSysErrOutput(handle: ProcessHandle, expectedOutput: String) {
            val errOut = handle.err
                .lines()
                .filter {
                    !it.startsWith("Picked up _JAVA_OPTIONS") &&
                            // FIXME #49 - replace Nashorn with GraalVM.js
                            !it.startsWith("Warning: Nashorn")
                }
            assertThat(errOut, equalTo(expectedOutput.lines()))
        }

        fun sendHttpRequest(request: String): RawHttpResponse<*> {
            var response: EagerHttpResponse<*>? = null
            var failedConnectionAttempts = 0
            lateinit var error: Exception
            while (failedConnectionAttempts < 5) {
                try {
                    response = TcpRawHttpClient().use { client ->
                        client.send(RawHttp().parseRequest(request)).eagerly(false)
                    }
                    break
                } catch (e: IOException) {
                    error = e
                    failedConnectionAttempts++
                    println("Connection to server failed, retry attempt number $failedConnectionAttempts")
                    sleep(250)
                }
            }

            return response
                ?: throw AssertionError(
                    "Unable to connect to server after " +
                            "$failedConnectionAttempts failed attempts", error
                )
        }

        fun asClassPathFile(file: String): String =
            RawHttpCliTester::class.java.getResource("/$file").file

        fun ProcessHandle.verifyProcessTerminatedWithExitCode(expectedExitCode: Int) {
            val statusCode = waitForEndAndGetStatus()
            if (statusCode != expectedExitCode) {
                throw AssertionError(
                    "Expected process to exit with code $expectedExitCode " +
                            "but was $statusCode\n\nProcess sysout:\n$out\n\nProcess syserr:\n$err"
                )
            }
        }

        fun ProcessHandle.verifyProcessTerminatedWithSigKillExitCode() {
            val sigKillCode = if (IS_WINDOWS) 1 else 143
            verifyProcessTerminatedWithExitCode(sigKillCode)
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

class ManualTest : RawHttpCliTester() {
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            startHttpServer()
            println("Server running on http://localhost:8083")
            println("Hit Enter to stop the server")
            System.`in`.read()
            stopHttpServer()
        }
    }

    override val cliRunner: CliRunner
        get() = CliRunner.JavaImage
}

fun lastModifiedHeaderValue(file: File): String =
    dateHeaderValue(Instant.ofEpochMilli(file.lastModified()))

fun dateHeaderValue(instant: Instant): String =
    RFC_1123_DATE_TIME.format(instant.atZone(ZoneOffset.UTC))
