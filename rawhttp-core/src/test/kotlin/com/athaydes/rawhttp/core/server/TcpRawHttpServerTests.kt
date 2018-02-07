package com.athaydes.rawhttp.core.server

import com.athaydes.rawhttp.core.bePresent
import com.athaydes.rawhttp.core.body.StringBody
import com.athaydes.rawhttp.core.client.TcpRawHttpClient
import com.athaydes.rawhttp.core.client.waitForPortToBeTaken
import io.kotlintest.Spec
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.specs.StringSpec

class TcpRawHttpServerTests : StringSpec() {

    private val server = TcpRawHttpServer(8093)
    private val http = server.options.rawHttp

    private val httpClient = TcpRawHttpClient()

    private fun startServer() {
        server.start { path: String ->
            when (path) {
                "/hello", "/" -> RequestHandler { req ->
                    when (req.method) {
                        "GET" ->
                            http.parseResponse("HTTP/1.1 200 OK\n" +
                                    "Content-Type: text/plain"
                            ).replaceBody(StringBody("Hello RawHTTP!"))
                        "DELETE" ->
                            throw Exception("Cannot delete")
                        else ->
                            http.parseResponse("HTTP/1.1 405 Method Not Allowed\n" +
                                    "Content-Type: text/plain"
                            ).replaceBody(StringBody("Sorry, can't handle this method"))
                    }
                }
                "/throw" -> throw Exception("Not doing it!")
                else -> RequestHandler { req ->
                    http.parseResponse("HTTP/1.1 404 Not Found\n" +
                            "Content-Type: text/plain"
                    ).replaceBody(StringBody("Content was not found"))
                }
            }
        }
    }

    private fun cleanup() {
        server.stop()
        httpClient.close()
    }

    override fun interceptSpec(context: Spec, spec: () -> Unit) {
        startServer()
        waitForPortToBeTaken(8093)
        try {
            spec()
        } finally {
            cleanup()
        }
    }

    init {
        "Server can handle successful http client request" {
            val request = http.parseRequest("GET http://localhost:8093/hello")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 200
            response.body should bePresent {
                it.asString(Charsets.UTF_8) shouldBe "Hello RawHTTP!"
            }
        }

        "Server can handle wrong method http client request" {
            val request = http.parseRequest("POST http://localhost:8093")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 405
            response.body should bePresent {
                it.asString(Charsets.UTF_8) shouldBe "Sorry, can't handle this method"
            }
        }

        "Server can handle wrong path http client request" {
            val request = http.parseRequest("GET http://localhost:8093/wrong/path")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 404
            response.body should bePresent {
                it.asString(Charsets.UTF_8) shouldBe "Content was not found"
            }
        }

        "Server returns default error response when router throws an Exception" {
            val request = http.parseRequest("POST http://localhost:8093/throw")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 500
            response.body should bePresent {
                it.asString(Charsets.UTF_8) shouldBe "A Server Error has occurred."
            }
        }

        "Server returns default error response when request handler throws an Exception" {
            val request = http.parseRequest("DELETE http://localhost:8093")
            val response = httpClient.send(request).eagerly()

            response.statusCode shouldBe 500
            response.body should bePresent {
                it.asString(Charsets.UTF_8) shouldBe "A Server Error has occurred."
            }
        }
    }

}