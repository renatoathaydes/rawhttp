package rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import io.kotlintest.specs.StringSpec
import rawhttp.core.errors.InvalidHttpHeader

class HttpHeadersTest : StringSpec({

    "Headers are multi-value, case-insensitive" {
        RawHttpHeaders.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .build().run {
                    get("hi") shouldEqual listOf("aaa", "bbb")
                    get("ho") shouldEqual listOf("ccc")
                    get("Hi") shouldEqual get("hi")
                    get("Hi") shouldEqual get("HI")
                    get("Ho") shouldEqual get("ho")
                    get("Ho") shouldEqual get("hO")
                    getFirst("hi") should bePresent { it shouldEqual "aaa" }
                    getFirst("HI") should bePresent { it shouldEqual "aaa" }
                    getFirst("ho") should bePresent { it shouldEqual "ccc" }
                    getFirst("Ho") should bePresent { it shouldEqual "ccc" }
                    getFirst("HI") shouldEqual getFirst("hi")
                    getFirst("HO") shouldEqual getFirst("ho")
                    get("blah") should beEmpty()
                    getFirst("blah") should notBePresent()

                    contains("hi") shouldBe true
                    contains("Hi") shouldBe true
                    contains("hI") shouldBe true
                    contains("HI") shouldBe true
                    contains("Ho") shouldBe true
                    contains("Blah") shouldBe false

                    headerNames shouldEqual listOf("hi", "hi", "ho")
                    uniqueHeaderNames shouldEqual setOf("HI", "HO")
                }
    }

    "Headers can be re-constructed exactly" {
        RawHttpHeaders.newBuilder()
                .with("Content-Type", "33")
                .with("Accept", "application/json")
                .with("Accept", "text/html")
                .with("Server", "nginx")
                .with("Accept", "text/plain")
                .with("Date", "22 March 2012")
                .build().toString() shouldEqual "" +
                "Content-Type: 33\r\n" +
                "Accept: application/json\r\n" +
                "Accept: text/html\r\n" +
                "Server: nginx\r\n" +
                "Accept: text/plain\r\n" +
                "Date: 22 March 2012\r\n"
    }

    "Headers may be added to other headers" {
        RawHttpHeaders.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .with("Accept", "application/json")
                .build().and(RawHttpHeaders.newBuilder()
                        .with("hi", "bye")
                        .with("Accept", "text/xml")
                        .with("Accept", "text/plain")
                        .with("New", "True").build()).run {
                    get("hi") shouldEqual listOf("bye")
                    get("ho") shouldEqual listOf("ccc")
                    get("Accept") shouldEqual listOf("text/xml", "text/plain")
                    get("New") shouldEqual listOf("True")
                    headerNames shouldBe listOf("hi", "ho", "Accept", "Accept", "New")
                }
    }

    "Headers may be built from other headers" {
        RawHttpHeaders.newBuilder(RawHttpHeaders.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .with("Accept", "application/json")
                .build()).with("hi", "bye")
                .with("Accept", "text/xml")
                .with("Accept", "text/plain")
                .with("New", "True").build().run {
                    get("hi") shouldEqual listOf("aaa", "bbb", "bye")
                    get("ho") shouldEqual listOf("ccc")
                    get("Accept") shouldEqual listOf("application/json", "text/xml", "text/plain")
                    get("New") shouldEqual listOf("True")
                    headerNames shouldBe listOf("hi", "hi", "ho", "Accept", "hi", "Accept", "Accept", "New")
                }
    }

    "Headers may be merged with other headers" {
        RawHttpHeaders.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .with("Accept", "application/json")
                .merge(RawHttpHeaders.newBuilder()
                        .with("hi", "bye")
                        .with("Accept", "text/xml")
                        .with("Accept", "text/plain")
                        .with("New", "True").build()).build().run {
                    get("hi") shouldEqual listOf("aaa", "bbb", "bye")
                    get("ho") shouldEqual listOf("ccc")
                    get("Accept") shouldEqual listOf("application/json", "text/xml", "text/plain")
                    get("New") shouldEqual listOf("True")
                    headerNames shouldBe listOf("hi", "hi", "ho", "Accept", "hi", "Accept", "Accept", "New")
                }
    }

    "Header names must not contain invalid characters" {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder()
                    .with("ABC(D)", "aaa").build()
        }
        error.message shouldEqual "Invalid header name (contains illegal character at index 3)"
    }

    "Header names may contain invalid characters if skipping validation" {
        RawHttpHeaders.newBuilderSkippingValidation().with("ABC(D)", "aaa").build().run {
            headerNames shouldBe listOf("ABC(D)")
            get("ABC(D)") shouldBe listOf("aaa")
        }
    }

    "Header values must not contain invalid characters" {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder()
                    .with("Hello", "hal\u007Fl").build()
        }
        error.message shouldEqual "Invalid header value (contains illegal character at index 3)"
    }

    "Header values may contain invalid characters if skipping validation" {
        RawHttpHeaders.newBuilderSkippingValidation().with("Hello", "hallå").build().run {
            headerNames shouldBe listOf("Hello")
            get("Hello") shouldBe listOf("hallå")
        }
    }

    "Header builder may remove previous headers" {
        RawHttpHeaders.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .with("Accept", "application/json")
                .remove("hi")
                .build().run {
                    get("hi") shouldBe emptyList<String>()
                    get("ho") shouldEqual listOf("ccc")
                    get("Accept") shouldEqual listOf("application/json")
                    headerNames shouldBe listOf("ho", "Accept")
                }
    }

    "Header builder may overwrite previous values" {
        RawHttpHeaders.newBuilder()
                .with("hi", "aaa")
                .with("hi", "bbb")
                .with("ho", "ccc")
                .with("Accept", "application/json")
                .overwrite("hi", "z")
                .build().run {
                    get("hi") shouldEqual listOf("z")
                    get("ho") shouldEqual listOf("ccc")
                    get("Accept") shouldEqual listOf("application/json")
                    headerNames shouldBe listOf("hi", "ho", "Accept")
                }
    }

    "Header names are validated by default" {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder().with("A:B", "aaa")
        }

        error.message shouldBe "Invalid header name (contains illegal character at index 1)"
    }

    "Header values are validated by default" {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder().with("A", "abc+\u0000eedd")
        }

        error.message shouldBe "Invalid header value (contains illegal character at index 4)"
    }

    "Header names are NOT validated if asked" {
        RawHttpHeaders.newBuilderSkippingValidation().with("A:B", "aaa")
    }

    "Header values are NOT validated if asked" {
        RawHttpHeaders.newBuilderSkippingValidation().with("A", "abc+åäö")
    }

})