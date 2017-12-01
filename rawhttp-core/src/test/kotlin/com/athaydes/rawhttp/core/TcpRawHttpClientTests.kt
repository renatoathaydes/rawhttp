package com.athaydes.rawhttp.core

import io.kotlintest.Spec
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec
import spark.Spark.get
import spark.Spark.port
import spark.Spark.post
import spark.Spark.stop
import java.net.Socket

fun sparkServerInterceptor(spec: Spec, runTest: () -> Unit) {
    println("Starting Spark")
    port(8083)
    get("/say-hi", "text/plain") { _, _ -> "Hi there" }
    get("/say-hi", "application/json") { _, _ -> "{ \"message\": \"Hi there\" }" }
    post("/echo", "text/plain") { req, _ -> req.body() }

    println("Spark is on")
    Thread.sleep(150)
    runTest()

    println("Stopping Spark")
    stop()
}

class TcpRawHttp10ClientTest : StringSpec() {

    override val specInterceptors: List<(Spec, () -> Unit) -> Unit>
        get() = listOf(::sparkServerInterceptor)

    init {
        "Must be able to perform a simple HTTP 1.0 request against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "GET http://localhost:8083/say-hi HTTP/1.0\r\n\r\n\r\n")).run {
                    String(bodyReader.eager().asBytes()) shouldBeOneOf setOf("Hi there", "{ \"message\": \"Hi there\" }")
                }
            }
        }

        "Must be able to perform a HTTP 1.0 request with headers against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "GET http://localhost:8083/say-hi HTTP/1.0\r\n" +
                                "Host: localhost\r\n" +
                                "Accept: text/plain\r\n\r\n\r\n")).run {
                    String(bodyReader.eager().asBytes()) shouldBe "Hi there"
                }
            }
        }

        "Must be able to perform a HTTP 1.0 request with headers and a body against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "POST http://localhost:8083/echo HTTP/1.0\r\n" +
                                "Host: localhost\r\n" +
                                "Accept: text/plain\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: 11\r\n" +
                                "\r\n" +
                                "hello world")).run {
                    String(bodyReader.eager().asBytes()) shouldBe "hello world"
                }
            }
        }
    }

}

class TcpRawHttp11ClientTest : StringSpec() {

    override val specInterceptors: List<(Spec, () -> Unit) -> Unit>
        get() = listOf(::sparkServerInterceptor)

    init {
        "Must be able to perform a simple HTTP 1.1 request against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "GET http://localhost:8083/say-hi HTTP/1.1\r\n\r\n\r\n")).run {
                    String(bodyReader.eager().asBytes()) shouldBeOneOf setOf("Hi there", "{ \"message\": \"Hi there\" }")
                }
            }
        }

        "Must be able to perform a HTTP 1.1 request with headers against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "GET http://localhost:8083/say-hi HTTP/1.1\r\n" +
                                "Host: localhost\r\n" +
                                "Accept: text/plain\r\n\r\n\r\n")).run {
                    String(bodyReader.eager().asBytes()) shouldBe "Hi there"
                }
            }
        }

        "Must be able to perform a HTTP 1.1 request with headers and a body against a real HTTP server" {
            Socket("localhost", 8083).use { socket ->
                TcpRawHttpClient(socket).send(RawHttp().parseRequest(
                        "POST http://localhost:8083/echo HTTP/1.1\r\n" +
                                "Host: localhost\r\n" +
                                "Accept: text/plain\r\n" +
                                "Content-Type: text/plain\r\n" +
                                "Content-Length: 11\r\n" +
                                "\r\n" +
                                "hello world")).run {
                    String(bodyReader.eager().asBytes()) shouldBe "hello world"
                }
            }
        }

    }

}