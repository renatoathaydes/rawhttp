package rawhttp.core

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec
import rawhttp.core.errors.InvalidHttpRequest

class RawHttpErrorsTest : StringSpec({

    "Cannot parse invalid request" {
        val examples = table(
                headers("Request", "lineNumber", "message"),
                row("", 0, "No content"),
                row("    ", 1, "Invalid request line: '    '"),
                row("POST", 1, "Invalid request line: 'POST'"),
                row("A B C D", 1, "Unknown HTTP version"),
                row("GET / HTTP/1.1\r\nINVALID\r\n", 2, "Invalid header: missing the ':' separator"),
                row("GET / HTTP/1.1\r\nAccept: all\r\nINVALID\r\n", 3, "Invalid header: missing the ':' separator"),
                row("GET / HTTP/1.1\r\nAccept: all\r\n", 1, "Host not given either in request line or Host header"),
                row("GET /path HTTP/1.1", 1, "Host not given either in request line or Host header"),
                row("GET /\r\nHost: hi.com\r\nAccept: */*\r\nHost: hi.com", 4, "More than one Host header specified"),
                row("GET /\r\nHost: ^&^%", 2, "Invalid host header: Invalid host format: " +
                        "Illegal character in authority at index 7: http://^&^%/")
        )
        forAll(examples) { request, expectedLineNumber, expectedMessage ->
            shouldThrow<InvalidHttpRequest> {
                RawHttp().parseRequest(request)
            }.run {
                lineNumber shouldBe expectedLineNumber
                message shouldBe expectedMessage
            }
        }
    }

    "Cannot parse invalid request (strict Host header requirement)" {
        shouldThrow<InvalidHttpRequest> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotInsertHostHeaderIfMissing()
                    .build()).parseRequest("GET http://hello.com HTTP/1.1\r\nAccept: */*")
        }.run {
            lineNumber shouldBe 1
            message shouldBe "Host header is missing"
        }
    }

    "Cannot parse invalid request (strict CRLF checks)" {
        val examples = table(
                headers("Request", "lineNumber", "message"),
                row("GET / HTTP/1.1\nAccept: all\r\n", 1, "Illegal new-line character without preceding return"),
                row("GET / HTTP/1.1\r\nAccept: all\n", 2, "Illegal new-line character without preceding return")
        )
        forAll(examples) { request, expectedLineNumber, expectedMessage ->
            shouldThrow<InvalidHttpRequest> {
                RawHttp(RawHttpOptions.Builder.newBuilder()
                        .doNotAllowNewLineWithoutReturn()
                        .build()
                ).parseRequest(request)
            }.run {
                lineNumber shouldBe expectedLineNumber
                message shouldBe expectedMessage
            }
        }
    }

    "Cannot parse invalid request (strict CRLF checks on trailing-part headers)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nHi: true\n\r\n").eagerly()
        }.run {
            message shouldBe "Illegal new-line character without preceding return (trailer header)"
        }
    }

    "Cannot parse invalid request (invalid trailing-part header)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nHi\r\n\r\n").eagerly()
        }.run {
            message shouldBe "Invalid header: missing the ':' separator (trailer header)"
        }
    }

    "Cannot parse invalid request (chunk too big)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\n12345678\r\n0\r\n\r\n").eagerly()
        }.run {
            message shouldBe "Invalid chunk-size (too big)"
        }
    }

    "Cannot parse invalid request (chunk-size invalid)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\nERR\r\n0\r\n\r\n").eagerly()
        }.run {
            message shouldBe "Illegal character in chunk-size: 'R'"
        }
    }

    "Cannot parse invalid request (EOF while reading chunk)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\nA\r\nXX").eagerly()
        }.run {
            message shouldBe "Unexpected EOF while reading chunk data"
        }
    }

    "Cannot parse invalid request (missing 0-size chunk)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\n2\r\nXX\r\n").eagerly()
        }.run {
            message shouldBe "Missing chunk-size"
        }
    }

    "Cannot parse invalid request (starting with new-line, if configured to not allow it)" {
        shouldThrow<InvalidHttpRequest> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotIgnoreLeadingEmptyLine()
                    .build()
            ).parseRequest("\r\nGET http://localhost").eagerly()
        }.run {
            message shouldBe "No content"
            lineNumber shouldBe 0
        }
    }

})