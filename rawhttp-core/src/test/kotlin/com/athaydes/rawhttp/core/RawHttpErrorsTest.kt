package com.athaydes.rawhttp.core

import com.athaydes.rawhttp.core.errors.InvalidHttpRequest
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.properties.forAll
import io.kotlintest.properties.headers
import io.kotlintest.properties.row
import io.kotlintest.properties.table
import io.kotlintest.specs.StringSpec

class RawHttpErrorsTest : StringSpec({

    "Cannot parse invalid request" {
        val examples = table(
                headers("Request", "lineNumber", "message"),
                row("", 0, "No content"),
                row("    ", 1, "Invalid method line"),
                row("POST", 1, "Invalid method line"),
                row("A B C D", 1, "Invalid method line"),
                row("GET / HTTP/1.1\r\nINVALID\r\n", 2, "Invalid header"),
                row("GET / HTTP/1.1\r\nAccept: all\r\nINVALID\r\n", 3, "Invalid header"),
                row("GET / HTTP/1.1\r\nAccept: all\r\n", 1, "Host not given neither in method line nor Host header")
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
            message shouldBe "Illegal new-line character without preceding return (parsing chunked body headers)"
        }
    }

    "Cannot parse invalid request (invalid trailing-part header)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\n0\r\nHi\r\n\r\n").eagerly()
        }.run {
            message shouldBe "Invalid header (parsing chunked body headers)"
        }
    }

    "Cannot parse invalid request (chunk too big)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\n12345\r\n0\r\n\r\n").eagerly()
        }.run {
            message shouldBe "Invalid chunk-size (too big, more than 4 hex-digits)"
        }
    }

    "Cannot parse invalid request (chunk-size invalid)" {
        shouldThrow<IllegalStateException> {
            RawHttp(RawHttpOptions.Builder.newBuilder()
                    .doNotAllowNewLineWithoutReturn()
                    .build()
            ).parseRequest("GET http://localhost\r\nTransfer-Encoding: chunked\r\n\r\nERR\r\n0\r\n\r\n").eagerly()
        }.run {
            message shouldBe "Invalid chunk-size (For input string: \"ERR\")"
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

})