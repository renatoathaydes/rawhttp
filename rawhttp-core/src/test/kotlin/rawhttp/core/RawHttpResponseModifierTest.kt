package rawhttp.core

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.body.StringBody

class RawHttpResponseModifierTest {

    private val response = RawHttpResponse(null, null,
            StatusLine(HttpVersion.HTTP_1_1, 200, "OK"), RawHttpHeaders.newBuilder()
            .with("Content-Type", "text/html")
            .with("Server", "RawHTTP").build(), null)

    @Test
    fun canPrependHeaders() {
        val newResponse = response.withHeaders(RawHttpHeaders.newBuilder()
                .with("Foo", "bar")
                .with("Server", "Apache").build(),
                false)
        newResponse.headers.run {
            this["Foo"] shouldBe listOf("bar")
            this["Server"] shouldBe listOf("Apache")
            this["Content-Type"] shouldBe listOf("text/html")
            headerNames shouldBe listOf("Foo", "Server", "Content-Type")
        }
    }

    @Test
    fun canAppendHeaders() {
        val newResponse = response.withHeaders(RawHttpHeaders.newBuilder()
                .with("Foo", "bar")
                .with("Server", "Apache").build(),
                true)
        newResponse.headers.run {
            this["Foo"] shouldBe listOf("bar")
            this["Server"] shouldBe listOf("Apache")
            this["Content-Type"] shouldBe listOf("text/html")
            headerNames shouldBe listOf("Content-Type", "Server", "Foo")
        }
    }

    @Test
    fun canAddBody() {
        val newResponse = response.withBody(StringBody("hello world", "text/plain"))

        // headers should be adjusted according to the body
        newResponse.headers.run {
            this["Content-Type"] shouldBe listOf("text/plain")
            this["Content-Length"] shouldBe listOf("11")
            this["Server"] shouldBe listOf("RawHTTP")
            asMap().size shouldBe 3
        }
    }

    @Test
    fun canAddBodyWhileKeepingHeadersUntouched() {
        val newResponse = response.withBody(StringBody("hello world", "text/plain"), false)

        newResponse.headers.run {
            this["Content-Type"] shouldBe listOf("text/html")
            this["Server"] shouldBe listOf("RawHTTP")
            asMap().size shouldBe 2
        }
    }

    @Test
    fun canRemoveBody() {
        val newResponse = response.withBody(null)

        // headers should be adjusted according to the body
        newResponse.headers.run {
            this["Server"] shouldBe listOf("RawHTTP")
            asMap().size shouldBe 1
        }
    }

    @Test
    fun canRemoveBodyWhileKeepingHeadersUntouched() {
        val newResponse = response.withBody(null, false)

        newResponse.headers.run {
            this["Content-Type"] shouldBe listOf("text/html")
            this["Server"] shouldBe listOf("RawHTTP")
            asMap().size shouldBe 2
        }
    }

}