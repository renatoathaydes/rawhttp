import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.startsWith
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThat
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
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
            assertThat(exitValue, equalTo(0))
            assertNoSysErrOutput(handle)
            assertThat(handle.out, startsWith("=============== RawHTTP CLI ==============="))
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
                ?: return fail("Cannot run test, no files found in the working directory: ${workDir.absolutePath}")

        val handle = runCli("serve", ".")

        val response = try {
            sendHttpRequest("""
            GET http://0.0.0.0:8080/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent()).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            throw e
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat(response.statusCode, equalTo(200))
        assertTrue(response.body.isPresent)
        assertThat(response.body.get().asRawBytes(), equalTo(someFileInWorkDir.readBytes()))
    }

    @Test
    fun canServeLocalDirectoryFromCustomRootPath() {
        val workDir = File(".")
        val someFileInWorkDir = workDir.listFiles()?.firstOrNull { it.isFile }
                ?: return fail("Cannot run test, no files found in the working directory: ${workDir.absolutePath}")
        val contextPath = "some/example"

        val handle = runCli("serve", ".", "-r", contextPath)

        val response = try {
            sendHttpRequest("""
            GET http://0.0.0.0:8080/$contextPath/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent()).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            handle.sendStopSignalToRawHttpServer()
            throw e
        }

        val responseToStandardPath = try {
            sendHttpRequest("""
            GET http://0.0.0.0:8080/${someFileInWorkDir.name}
            Accept: */*
            """.trimIndent()).eagerly()
        } catch (e: AssertionError) {
            println(handle)
            throw e
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat(response.statusCode, equalTo(200))
        assertTrue(response.body.isPresent)
        assertThat(response.body.get().asRawBytes(), equalTo(someFileInWorkDir.readBytes()))

        assertThat(responseToStandardPath.statusCode, equalTo(404))
        assertTrue(responseToStandardPath.body.isPresent)
        assertThat(responseToStandardPath.body.get().asRawString(Charsets.UTF_8), equalTo("Resource was not found."))
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
            sendHttpRequest("""
            GET http://0.0.0.0:8080/${mp3File.name}
            Accept: */*
            """.trimIndent()).eagerly() to sendHttpRequest("""
            GET http://0.0.0.0:8080/${jsonFile.name}
            Accept: */*
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat(mp3response.statusCode, equalTo(200))
        assertTrue(mp3response.body.isPresent)
        assertThat(mp3response.body.get().asRawBytes(), equalTo(mp3File.readBytes()))
        assertThat(mp3response.headers["Content-Type"], equalTo(listOf("music/mp3")))

        // verify that the standard mappings are still used
        assertThat(jsonResponse.statusCode, equalTo(200))
        assertTrue(jsonResponse.body.isPresent)
        assertThat(jsonResponse.body.get().asRawBytes(), equalTo(jsonFile.readBytes()))
        assertThat(jsonResponse.headers["Content-Type"], equalTo(listOf("application/json")))
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
            sendHttpRequest("""
            GET http://0.0.0.0:8080/some.json
            Accept: application/json
            """.trimIndent()).eagerly() to sendHttpRequest("""
            GET http://0.0.0.0:8080/some.txt
            Accept: text/plain
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat(jsonResponse.statusCode, equalTo(200))
        assertTrue(jsonResponse.body.isPresent)
        assertThat(jsonResponse.body.get().asRawBytes(), equalTo(jsonFile.readBytes()))
        assertThat(jsonResponse.headers["Content-Type"], equalTo(listOf("application/json")))
        assertThat(jsonResponse.headers["Last-Modified"], equalTo(
                listOf(lastModifiedHeaderValue(jsonFile))))

        assertThat(textResponse.statusCode, equalTo(200))
        assertTrue(textResponse.body.isPresent)
        assertThat(textResponse.body.get().asRawBytes(), equalTo(textFile.readBytes()))
        assertThat(textResponse.headers["Content-Type"], equalTo(listOf("text/plain")))
        assertThat(textResponse.headers["Last-Modified"], equalTo(
                listOf(lastModifiedHeaderValue(textFile))))
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
            sendHttpRequest("""
            GET http://0.0.0.0:8080/some.json
            If-Modified-Since: ${dateHeaderValue(afterModified)}
            """.trimIndent()).eagerly() to sendHttpRequest("""
            GET http://0.0.0.0:8080/some.json
            If-Modified-Since: ${dateHeaderValue(beforeModified)}
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat(afterModifiedResponse.statusCode, equalTo(304))
        assertFalse(afterModifiedResponse.body.isPresent)
        assertThat(afterModifiedResponse.headers.headerNames, equalTo(listOf("Date", "Server")))

        assertThat(beforeModifiedResponse.statusCode, equalTo(200))
        assertTrue(beforeModifiedResponse.body.isPresent)
        assertThat(beforeModifiedResponse.body.get().asRawBytes(), equalTo(jsonFile.readBytes()))
        assertThat(beforeModifiedResponse.headers["Content-Type"], equalTo(listOf("application/json")))
        assertThat(beforeModifiedResponse.headers["Last-Modified"], equalTo(
                listOf(lastModifiedHeaderValue(jsonFile))))

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
            sendHttpRequest("""
            GET http://0.0.0.0:8080/some.txt
            If-Unmodified-Since: ${dateHeaderValue(afterModified)}
            """.trimIndent()).eagerly() to sendHttpRequest("""
            GET http://0.0.0.0:8080/some.txt
            If-Unmodified-Since: ${dateHeaderValue(beforeModified)}
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat(afterModifiedResponse.statusCode, equalTo(200))
        assertTrue(afterModifiedResponse.body.isPresent)
        assertThat(afterModifiedResponse.body.get().asRawBytes(), equalTo(textFile.readBytes()))
        assertThat(afterModifiedResponse.headers["Content-Type"], equalTo(listOf("text/plain")))
        assertThat(afterModifiedResponse.headers["Last-Modified"], equalTo(
                listOf(lastModifiedHeaderValue(textFile))))

        assertThat(beforeModifiedResponse.statusCode, equalTo(412))

        // 412 response must have a body, check it's empty
        assertTrue(beforeModifiedResponse.body.isPresent)
        assertThat(beforeModifiedResponse.body.get().asRawString(Charsets.US_ASCII), equalTo(""))
        assertThat(beforeModifiedResponse.headers.headerNames, equalTo(listOf("Content-Length", "Date", "Server")))
        assertThat(beforeModifiedResponse.headers["Content-Length"], equalTo(listOf("0")))
    }

    @Test
    fun canServeAnyDirectoryLoggingRequests() {
        val tempDir = createTempDirectory(javaClass.name).toFile()
        val someFile = File(tempDir, "my-file")
        someFile.writeText("Hello RawHTTP!")

        val handle = runCli("serve", tempDir.absolutePath, "--log-requests")

        val response = try {
            sendHttpRequest("""
            GET http://0.0.0.0:8080/${someFile.name}
            Accept: */*
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat("Server returned unexpected status code\n$handle",
                response.statusCode, equalTo(200))
        assertTrue(response.body.isPresent)
        assertThat(response.body.get().asRawString(Charsets.UTF_8), equalTo("Hello RawHTTP!"))

        // log format follows the Common Log Format - https://en.wikipedia.org/wiki/Common_Log_Format
        val dateRegex = Regex("[0-9.:]+ \\[(?<date>.+)] \".+\" \\d{3} \\d+").toPattern()

        // verify the request was logged
        val lastOutputLine = handle.out.lines().asReversed().find { it.isNotEmpty() } ?: "<none>"
        val match = dateRegex.matcher(lastOutputLine)

        if (!match.find()) {
            return fail("Process output did not match the expected value:\n$handle")
        }

        val logDate = match.group("date")

        // should be able to parse the date with the formatter used in Common Log Format
        val dateFormat = DateTimeFormatter
                .ofPattern("d/MMM/yyyy:HH:mm:ss z")
                .withLocale(Locale.getDefault())
                .withZone(ZoneId.systemDefault())

        val parsedLogDate = LocalDateTime.parse(logDate, dateFormat)

        // should be very recent date
        assertTrue("Parsed Date seems too different from the expected: $parsedLogDate",
                parsedLogDate.isBefore(LocalDateTime.now().plusSeconds(5)) &&
                        parsedLogDate.isAfter(LocalDateTime.now().minusSeconds(10)))

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
            sendHttpRequest("""
            GET http://0.0.0.0:8080/../${parentDirFile.name}
            Accept: */*
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat("Server returned unexpected status code\n$handle",
                response.statusCode, equalTo(404))
        assertTrue(response.body.isPresent)
        assertThat(response.body.get().asRawString(Charsets.UTF_8), equalTo("Resource was not found."))
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
            sendHttpRequest("""
            GET http://0.0.0.0:8080/hello
            Accept: application/json
            """.trimIndent()).eagerly() to
                    sendHttpRequest("""
            GET http://0.0.0.0:8080/hello
            Accept: application/xml
            """.trimIndent()).eagerly()
        } finally {
            handle.sendStopSignalToRawHttpServer()
        }

        handle.verifyProcessTerminatedWithSigKillExitCode()

        assertThat("Server returned unexpected status code\n$handle",
                jsonResponse.statusCode, equalTo(200))
        assertTrue(jsonResponse.body.isPresent)
        assertThat(jsonResponse.body.get().asRawString(Charsets.UTF_8), equalTo(jsonFile.readText()))

        assertThat("Server returned unexpected status code\n$handle",
                xmlResponse.statusCode, equalTo(200))
        assertTrue(xmlResponse.body.isPresent)
        assertThat(xmlResponse.body.get().asRawString(Charsets.UTF_8), equalTo(xmlFile.readText()))
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
    fun canRunHttpFileWithEnvironmentAndPrintStats() {
        val handleProd = runCli("run", asClassPathFile("reqin-edit-tests/with-env/file.http"),
                "-l", "-e", "prod", "-p", "stats")
        assertGetFooThenPostFooRequestsAndStats(handleProd)
    }

    @Test
    fun canRunHttpFileUsingExternalFiles() {
        val handle = runCli("run", asClassPathFile("reqin-edit-tests/files/post.http"),
                "-p", "body")
        assertSuccessResponseReplyToFiles(handle)
        assertReplyResponseStoredInFile()
    }

    @Test
    fun canRunHttpFileWithTests() {
        val handle = runCli("run", asClassPathFile("reqin-edit-tests/tests/tests.http"),
                "-p", "status")
        assertHttpTestResults(handle)
    }

}
