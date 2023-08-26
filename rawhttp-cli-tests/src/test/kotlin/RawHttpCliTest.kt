import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.fail
import java.io.File
import java.lang.Thread.sleep
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.io.path.createTempDirectory

class RawHttpCliTest : RawHttpCliTester() {

    @Test
    fun canPrintHelp() {
        fun assertHelpOptOutput(handle: ProcessHandle) {
            val exitValue = handle.waitForEndAndGetStatus()
            exitValue shouldBe 0
            assertNoSysErrOutput(handle)
            handle.out shouldStartWith ("=============== RawHTTP CLI ===============")
        }

        val shortOptHandle = runCli("-h")
        assertHelpOptOutput(shortOptHandle)

        val longOptHandle = runCli("help")
        assertHelpOptOutput(longOptHandle)
    }

    @Test
    fun canReadRequestFromSysIn() {
        val handle = runCli("send")

        // write the request to the process sysin
        handle.process.outputStream.writer().use {
            it.write(SUCCESS_HTTP_REQUEST)
        }

        assertOutputIsSuccessResponse(handle)
    }

    @Test
    fun canReadRequestFromTextArgument() {
        val handle = runCli("send", "-t", SUCCESS_HTTP_REQUEST)
        assertOutputIsSuccessResponse(handle)
    }

    @Test
    fun canLogRequestFromTextArgument() {
        val handle = runCli("send", "-l", "-t", SUCCESS_HTTP_REQUEST)
        assertSuccessRequestIsLoggedThenSuccessResponse(handle)
    }

    @Test
    fun canLogResponseFull() {
        val handle = runCli("send", "-l", "-p", "response", "-t", SUCCESS_HTTP_REQUEST)
        assertSuccessRequestIsLoggedThenSuccessResponse(handle)
    }

    @Test
    fun canLogResponseAll() {
        val handle = runCli("send", "-p", "all", "-t", SUCCESS_HTTP_REQUEST)
        assertOutputIsSuccessResponseAndThenStatistics(handle)
    }

    @Test
    fun canLogResponseStatus() {
        val handle = runCli("send", "-p", "status", "-t", SUCCESS_HTTP_REQUEST)
        assertSuccessResponseStatus(handle)
    }

    @Test
    fun canLogResponseBody() {
        val handle = runCli("send", "-p", "body", "-t", SUCCESS_HTTP_REQUEST)
        assertSuccessResponseBody(handle)
    }

    @Test
    fun canLogResponseStats() {
        val handle = runCli("send", "-p", "stats", "-t", SUCCESS_HTTP_REQUEST)
        assertSuccessResponseStats(handle)
    }

    @Test
    fun serverReturns404OnNonExistentResource() {
        val handle = runCli("send", "-t", NOT_FOUND_HTTP_REQUEST)
        assertOutputIs404Response(handle)
    }

    @Test
    fun canReadRequestFromFile() {
        val tempFile = File.createTempFile(javaClass.name, "request")
        tempFile.writeText(SUCCESS_HTTP_REQUEST)

        val handle = runCli("send", "-f", tempFile.absolutePath)
        assertOutputIsSuccessResponse(handle)
    }

    @Test
    fun canLogRequestFromFile() {
        val tempFile = File.createTempFile(javaClass.name, "request")
        tempFile.writeText(SUCCESS_HTTP_REQUEST)

        val handle = runCli("send", "-f", tempFile.absolutePath, "--log-request")
        assertSuccessRequestIsLoggedThenSuccessResponse(handle)
    }

    @Test
    fun canServeLocalDirectory() {
        val workDir = File(".")
        val someFileInWorkDir = workDir.listFiles()?.firstOrNull { it.isFile }
            ?: fail("Cannot run test, no files found in the working directory: ${workDir.absolutePath}")

        val handle = runCli("serve", ".")

        val response = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent()
            ).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            throw e
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        response.statusCode shouldBe 200
        response.body shouldBePresent {
            asRawBytes() shouldBe someFileInWorkDir.readBytes()
        }
    }

    @Test
    fun canServeLocalDirectoryUsingTls() {
        val workDir = File(".")
        val someFileInWorkDir = workDir.listFiles()?.firstOrNull { it.isFile }
            ?: fail("Cannot run test, no files found in the working directory: ${workDir.absolutePath}")

        val keystore = System.getProperty("rawhttp.server.keystore")!!

        val handle = runCli("serve", "-k", keystore, "-w", "password", ".")

        val response = try {
            sendHttpRequest(
                """
            GET https://0.0.0.0:8080/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent(), ignoreTls = true
            ).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            throw e
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        response.statusCode shouldBe 200
        response.body shouldBePresent {
            asRawBytes() shouldBe someFileInWorkDir.readBytes()
        }
    }

    @Test
    fun canServeLocalDirectoryFromCustomRootPath() {
        val workDir = File(".")
        val someFileInWorkDir = workDir.listFiles()?.firstOrNull { it.isFile }
            ?: fail("Cannot run test, no files found in the working directory: ${workDir.absolutePath}")
        val contextPath = "some/example"

        val handle = runCli("serve", ".", "-r", contextPath)

        val response = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/$contextPath/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent()
            ).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            handle.sendStopSignalToRawHttpServer()
            throw e
        }

        val responseToStandardPath = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent()
            ).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            throw e
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        response.statusCode shouldBe 200
        response.body shouldBePresent {
            asRawBytes() shouldBe someFileInWorkDir.readBytes()
        }

        responseToStandardPath.statusCode shouldBe 404
        responseToStandardPath.body shouldBePresent {
            asRawString(Charsets.UTF_8) shouldBe "Resource was not found."
        }
    }

    @Test
    fun canServeResourceUsingCustomMediaTypes() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val mp3File = File(tempDir, "resource.mp3")
        mp3File.writeText("Music!!")
        val jsonFile = File(tempDir, "some.json")
        jsonFile.writeText("true")

        val mediaTypesFile = File(tempDir, "media.properties")
        mediaTypesFile.writeText("mp3: music/mp3")

        val handle = runCli("serve", tempDir.absolutePath, "--media-types", mediaTypesFile.absolutePath)

        val (mp3response, jsonResponse) = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/${mp3File.name}
            Accept: */*
            """.trimIndent()
            ).eagerly() to sendHttpRequest(
                """
            GET http://0.0.0.0:8080/${jsonFile.name}
            Accept: */*
            """.trimIndent()
            ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        mp3response.statusCode shouldBe 200
        mp3response.body shouldBePresent {
            asRawBytes() shouldBe mp3File.readBytes()
        }
        mp3response.headers["Content-Type"] shouldBe listOf("music/mp3")

        // verify that the standard mappings are still used
        jsonResponse.statusCode shouldBe 200
        jsonResponse.body shouldBePresent {
            asRawBytes() shouldBe jsonFile.readBytes()
        }
        jsonResponse.headers["Content-Type"] shouldBe listOf("application/json")
    }

    @Test
    fun servedResourcesUseLastModifiedHeaders() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val jsonFile = File(tempDir, "some.json")
        jsonFile.writeText("true")
        sleep(10L) // ensure different file modified timestamps
        val textFile = File(tempDir, "some.txt")
        textFile.writeText("foo")

        val handle = runCli("serve", tempDir.absolutePath)

        val (jsonResponse, textResponse) = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/some.json
            Accept: application/json
            """.trimIndent()
            ).eagerly() to sendHttpRequest(
                """
            GET http://0.0.0.0:8080/some.txt
            Accept: text/plain
            """.trimIndent()
            ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        jsonResponse.statusCode shouldBe 200
        jsonResponse.body shouldBePresent {
            asRawBytes() shouldBe jsonFile.readBytes()
        }
        jsonResponse.headers["Content-Type"] shouldBe listOf("application/json")
        jsonResponse.headers["Last-Modified"] shouldBe listOf(lastModifiedHeaderValue(jsonFile))

        textResponse.statusCode shouldBe 200
        textResponse.body shouldBePresent {
            asRawBytes() shouldBe textFile.readBytes()
        }
        textResponse.headers["Content-Type"] shouldBe listOf("text/plain")
        textResponse.headers["Last-Modified"] shouldBe listOf(lastModifiedHeaderValue(textFile))
    }

    @Test
    fun doNotServeResourceIfNotModified() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val jsonFile = File(tempDir, "some.json")
        jsonFile.writeText("true")

        val afterModified = Instant.now().plusSeconds(1)
        val beforeModified = Instant.now().minusSeconds(10)

        val handle = runCli("serve", tempDir.absolutePath)

        val (afterModifiedResponse, beforeModifiedResponse) = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/some.json
            If-Modified-Since: ${dateHeaderValue(afterModified)}
            """.trimIndent()
            ).eagerly() to sendHttpRequest(
                """
            GET http://0.0.0.0:8080/some.json
            If-Modified-Since: ${dateHeaderValue(beforeModified)}
            """.trimIndent()
            ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        afterModifiedResponse.statusCode shouldBe 304
        afterModifiedResponse.headers.headerNames shouldBe listOf("Date", "Server")

        beforeModifiedResponse.statusCode shouldBe 200
        beforeModifiedResponse.body shouldBePresent {
            asRawBytes() shouldBe jsonFile.readBytes()
        }
        beforeModifiedResponse.headers["Content-Type"] shouldBe listOf("application/json")
        beforeModifiedResponse.headers["Last-Modified"] shouldBe listOf(lastModifiedHeaderValue(jsonFile))
    }

    @Test
    fun doNotServeResourceIfNotUnModified() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val textFile = File(tempDir, "some.txt")
        textFile.writeText("hi")

        val afterModified = Instant.now().plusSeconds(1)
        val beforeModified = Instant.now().minusSeconds(10)

        val handle = runCli("serve", tempDir.absolutePath)

        val (afterModifiedResponse, beforeModifiedResponse) = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/some.txt
            If-Unmodified-Since: ${dateHeaderValue(afterModified)}
            """.trimIndent()
            ).eagerly() to sendHttpRequest(
                """
            GET http://0.0.0.0:8080/some.txt
            If-Unmodified-Since: ${dateHeaderValue(beforeModified)}
            """.trimIndent()
            ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        afterModifiedResponse.statusCode shouldBe 200
        afterModifiedResponse.body shouldBePresent {
            asRawBytes() shouldBe textFile.readBytes()
        }
        afterModifiedResponse.headers["Content-Type"] shouldBe listOf("text/plain")
        afterModifiedResponse.headers["Last-Modified"] shouldBe listOf(lastModifiedHeaderValue(textFile))

        beforeModifiedResponse.statusCode shouldBe 412
        // 412 response must have a body, check it's empty
        beforeModifiedResponse.body shouldBePresent {
            asRawString(Charsets.US_ASCII) shouldBe ""
        }
        beforeModifiedResponse.headers.headerNames shouldBe listOf("Content-Length", "Date", "Server")
        beforeModifiedResponse.headers["Content-Length"] shouldBe listOf("0")
    }

    @Test
    fun canServeAnyDirectoryLoggingRequests() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val someFile = File(tempDir, "my-file")
        someFile.writeText("Hello RawHTTP!")

        val handle = runCli("serve", tempDir.absolutePath, "--log-requests")

        val response = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/${someFile.name}
            Accept: */*
            """.trimIndent()
            ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        response.statusCode shouldBe 200
        response.body shouldBePresent {
            asRawString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
        }

        // log format follows the Common Log Format - https://en.wikipedia.org/wiki/Common_Log_Format
        val dateRegex = Regex("[0-9.:]+ \\[(?<date>.+)] \".+\" \\d{3} \\d+").toPattern()

        // verify the request was logged
        val lastOutputLine = handle.out.lines().asReversed().find { it.isNotEmpty() } ?: "<none>"
        val match = dateRegex.matcher(lastOutputLine)

        if (!match.find()) {
            fail("Process output did not match the expected value:\n$handle")
        }

        val logDate = match.group("date")

        // should be able to parse the date with the formatter used in Common Log Format
        val dateFormat = DateTimeFormatter
            .ofPattern("d/MMM/yyyy:HH:mm:ss z")
            .withLocale(Locale.getDefault())
            .withZone(ZoneId.systemDefault())

        val parsedLogDate = LocalDateTime.parse(logDate, dateFormat)

        // should be very recent date
        assert(
            parsedLogDate.isBefore(LocalDateTime.now().plusSeconds(5)) &&
                    parsedLogDate.isAfter(LocalDateTime.now().minusSeconds(10))
        ) {
            "Parsed Date seems too different from the expected: $parsedLogDate"
        }

        assertNoSysErrOutput(handle)
    }

    @Test
    fun doesNotExposeParentDirectoryWhenServingDirectory() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val parentDirFile = File(tempDir.parentFile, tempDir.name + ".test")
        parentDirFile.writeText("not visible")
        parentDirFile.deleteOnExit()

        val handle = runCli("serve", tempDir.absolutePath)

        val response = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/../${parentDirFile.name}
            Accept: */*
            """.trimIndent()
            ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        response.statusCode shouldBe 404
        response.body.shouldBePresent {
            asRawString(Charsets.UTF_8) shouldBe "Resource was not found."
        }
    }

    @Test
    fun canFindResourceWithoutExtension() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val jsonFile = File(tempDir, "hello.json")
        val xmlFile = File(tempDir, "hello.xml")
        jsonFile.writeText("{\"hello\": true}")
        xmlFile.writeText("<hello>true</hello>")
        tempDir.deleteOnExit()

        val handle = runCli("serve", tempDir.absolutePath)

        val (jsonResponse, xmlResponse) = try {
            sendHttpRequest(
                """
            GET http://0.0.0.0:8080/hello
            Accept: application/json
            """.trimIndent()
            ).eagerly() to
                    sendHttpRequest(
                        """
            GET http://0.0.0.0:8080/hello
            Accept: application/xml
            """.trimIndent()
                    ).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        jsonResponse.statusCode shouldBe 200
        jsonResponse.body.shouldBePresent {
            asRawString(Charsets.UTF_8) shouldBe jsonFile.readText()
        }

        xmlResponse.statusCode shouldBe 200
        xmlResponse.body.shouldBePresent {
            asRawString(Charsets.UTF_8) shouldBe xmlFile.readText()
        }
    }

    @Test
    fun canRunBasicHttpFile() {
        val handle = runCli("run", asClassPathFile("reqin-edit-tests/basic/get.http"))
        assertOutputIsSuccessResponse(handle)
    }

    @Test
    fun canRunBasicHttpFileLoggingRequest() {
        val handle = runCli("run", asClassPathFile("reqin-edit-tests/basic/get.http"), "--log-request")
        assertSuccessRequestIsLoggedThenSuccessResponse(handle)
    }

    @Test
    fun canRunHttpFileWithEnvironment() {
        val handleProd = runCli("run", asClassPathFile("reqin-edit-tests/with-env/file.http"), "-e", "prod")
        assertGetFooResponseThenPostFooResponse(handleProd, "{prod: true}")

        val handleTest = runCli("run", asClassPathFile("reqin-edit-tests/with-env/file.http"), "-e", "test")
        assertGetFooResponseThenPostFooResponse(handleTest, "{prod: false}")
    }

    @Test
    fun canRunHttpFileWithEnvironmentAndComments() {
        val handle = runCli("run", asClassPathFile("reqin-edit-tests/with-env/variables.http"), "-e", "prod")
        assertPostMirrorHeadersAndBody(handle)
    }

    @Test
    fun canRunHttpFileWithEnvironmentAndPrintStats() {
        val handleProd = runCli(
            "run", asClassPathFile("reqin-edit-tests/with-env/file.http"),
            "-l", "-e", "prod", "-p", "stats"
        )
        assertGetFooThenPostFooRequestsAndStats(handleProd)
    }

    @Test
    fun canRunHttpFileUsingExternalFiles() {
        val handle = runCli(
            "run", asClassPathFile("reqin-edit-tests/files/post.http"),
            "-p", "body"
        )
        assertSuccessResponseReplyToFiles(handle)
        assertReplyResponseStoredInFile()
    }

    @Test
    fun canRunHttpFileWithTests() {
        val handle = runCli(
            "run", asClassPathFile("reqin-edit-tests/tests/tests.http"),
            "-p", "status"
        )
        assertHttpTestResults(handle)
    }

    @Test
    fun canRunHttpFileIgnoringTlsCertificate() {
        val handle = runCli(
            "run", asClassPathFile("reqin-edit-tests/tests/tests.http"),
            "--ignore-tls-cert"
        )
        handle.verifyProcessTerminatedWithExitCode(0)
        assertNoSysErrOutput(handle)
    }

}
