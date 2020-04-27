package rawhttp.core

import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import org.junit.Test
import rawhttp.core.body.BytesBody

class EagerHttpResponseTest {

    @Test
    fun bodyTrailerHeadersAreAddedToTheResponseHeaders() {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\n"

        RawHttp().parseResponse("HTTP/1.1 200 OK\r\n" +
                "Content-Type: http/response\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                body).eagerly().run {
            getBody().isPresent shouldBe true
            getBody().get().decodeBodyToString(Charsets.UTF_8) shouldBe "98"
            headers.asMap() shouldEqual mapOf(
                    "CONTENT-TYPE" to listOf("http/response"),
                    "TRANSFER-ENCODING" to listOf("chunked"),
                    "HELLO" to listOf("hi there", "wow"),
                    "BYE" to listOf("true")
            )

            // ensure we can consume the body more than once
            getBody().get().eager().decodeBodyToString(Charsets.UTF_8) shouldBe "98"
        }
    }

}

class EagerHttpRequestTest {

    @Test
    fun bodyTrailerHeadersAreAddedToTheRequestHeaders() {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\n"

        RawHttp().parseRequest("POST /foo HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-Type: http/request\r\n" +
                "Transfer-Encoding: chunked\r\n" +
                "\r\n" +
                body).eagerly().run {
            getBody().isPresent shouldBe true
            getBody().get().decodeBodyToString(Charsets.UTF_8) shouldBe "98"
            headers.asMap() shouldEqual mapOf(
                    "HOST" to listOf("localhost"),
                    "CONTENT-TYPE" to listOf("http/request"),
                    "TRANSFER-ENCODING" to listOf("chunked"),
                    "HELLO" to listOf("hi there", "wow"),
                    "BYE" to listOf("true")
            )

            // ensure we can consume the body more than once
            getBody().get().eager().decodeBodyToString(Charsets.UTF_8) shouldBe "98"
        }
    }

    @Test
    fun shouldBePossibleToReplaceBodyAndGetEagerHttpRequest() {
        val request = RawHttp().parseRequest("POST /foo HTTP/1.1\r\n" +
                "Host: localhost\r\n" +
                "Content-Type: http/request\r\n" +
                "Content-Length: 4\r\n" +
                "\r\n" +
                "body")

        request.withBody(BytesBody("some-bytes".toByteArray())).eagerly().run {
            body.isPresent shouldBe true
            body.get().decodeBodyToString(Charsets.US_ASCII) shouldBe "some-bytes"
            eagerly().body.get().decodeBodyToString(Charsets.US_ASCII) shouldBe "some-bytes"
        }
    }

}