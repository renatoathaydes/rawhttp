package rawhttp.core

import io.kotest.assertions.fail
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.data.forAll
import io.kotest.data.headers
import io.kotest.data.row
import io.kotest.data.table
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.errors.InvalidHttpResponse

class StatusLineTest {

    val metadataParser = HttpMetadataParser(RawHttpOptions.defaultInstance())

    val strictMetadataParser = HttpMetadataParser(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .doNotIgnoreLeadingEmptyLine()
            .doNotInsertHttpVersionIfMissing()
            .build())

    @Test
    fun `Can parse legal status-line (allow missing HTTP version)`() {
        val table = table(headers("Status Line", "Expected version", "Expected status code", "Expected phrase"),
                row("200", HttpVersion.HTTP_1_1, 200, ""),
                row("200 OK", HttpVersion.HTTP_1_1, 200, "OK"),
                row("404 Not Found", HttpVersion.HTTP_1_1, 404, "Not Found"),
                row("500 Internal Server Error", HttpVersion.HTTP_1_1, 500, "Internal Server Error"),
                row("HTTP/1.0 200", HttpVersion.HTTP_1_0, 200, ""),
                row("HTTP/1.0 200 OK", HttpVersion.HTTP_1_0, 200, "OK"),
                row("HTTP/1.1 404", HttpVersion.HTTP_1_1, 404, ""),
                row("HTTP/1.1 500 Server Error", HttpVersion.HTTP_1_1, 500, "Server Error"),
                row("HTTP/1.0 200 My Custom Error Phrase", HttpVersion.HTTP_1_0, 200, "My Custom Error Phrase"),
                row("HTTP/0.9 200", HttpVersion.HTTP_0_9, 200, ""),
                row("HTTP/0.9 200 OK", HttpVersion.HTTP_0_9, 200, "OK"))

        forAll(table) { statusLine, expectedVersion, expectedStatusCode, expectedPhrase ->
            try {
                metadataParser.parseStatusLine(statusLine).run {
                    httpVersion shouldBe expectedVersion
                    statusCode shouldBe expectedStatusCode
                    reason shouldBe expectedPhrase
                }
            } catch (e: InvalidHttpResponse) {
                fail(e.toString())
            }
        }
    }

    @Test
    fun `Cannot parse illegal status-line (allow missing HTTP version)`() {
        val table = table(headers("Status Line", "Expected error"),
                row("", "No content"),
                row("OK", "Invalid status code"),
                row("Accept: application/json", "Invalid status code"),
                row("OK 200", "Invalid status code"),
                row("HTTP/1.1 OK", "Invalid status code"),
                row("HTTP/1.2 200 OK", "Invalid HTTP version"))

        forAll(table) { statusLine, expectedError ->
            val error = shouldThrow<InvalidHttpResponse> { metadataParser.parseStatusLine(statusLine) }
            error.message shouldBe expectedError
            error.lineNumber shouldBe if (statusLine.isEmpty()) 0 else 1
        }
    }

    @Test
    fun `Can parse legal status-line (strict)`() {
        val table = table(headers("Status Line", "Expected version", "Expected status code", "Expected phrase"),
                row("HTTP/1.0 200 OK", HttpVersion.HTTP_1_0, 200, "OK"),
                row("HTTP/1.1 404", HttpVersion.HTTP_1_1, 404, ""),
                row("HTTP/1.1 500 Server Error", HttpVersion.HTTP_1_1, 500, "Server Error"),
                row("HTTP/1.0 200 My Custom Error Phrase", HttpVersion.HTTP_1_0, 200, "My Custom Error Phrase"),
                row("HTTP/0.9 200", HttpVersion.HTTP_0_9, 200, ""),
                row("HTTP/0.9 200 OK", HttpVersion.HTTP_0_9, 200, "OK"))

        forAll(table) { statusLine, expectedVersion, expectedStatusCode, expectedPhrase ->
            try {
                strictMetadataParser.parseStatusLine(statusLine).run {
                    httpVersion shouldBe expectedVersion
                    statusCode shouldBe expectedStatusCode
                    reason shouldBe expectedPhrase
                }
            } catch (e: InvalidHttpResponse) {
                fail(e.toString())
            }
        }
    }

    @Test
    fun `Cannot parse illegal status-line (strict)`() {
        val table = table(headers("Status Line", "Expected error"),
                row("", "No content"),
                row("OK", "Missing HTTP version"),
                row("Accept: application/json", "Missing HTTP version"),
                row("OK 200", "Missing HTTP version"),
                row("HTTP/1.2 200 OK", "Invalid HTTP version"),
                row("HTTP/1.1 OK", "Invalid status code"),
                row("HTTP/1.1 OK 200", "Invalid status code"),
                row("200", "Missing HTTP version"),
                row("200 OK", "Missing HTTP version"),
                row("404 Not Found", "Missing HTTP version"))

        forAll(table) { statusLine, expectedError ->
            val error = shouldThrow<InvalidHttpResponse> { strictMetadataParser.parseStatusLine(statusLine) }
            error.message shouldBe expectedError
            error.lineNumber shouldBe if (statusLine.isEmpty()) 0 else 1
        }
    }
}
