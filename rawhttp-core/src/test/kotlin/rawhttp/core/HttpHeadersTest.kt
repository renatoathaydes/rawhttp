package rawhttp.core

import io.kotlintest.matchers.beEmpty
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.matchers.shouldThrow
import org.junit.Test
import rawhttp.core.errors.InvalidHttpHeader

class HttpHeadersTest {

    @Test
    fun headersAreMultiValueCaseInsensitive() {
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

    @Test
    fun headersCanContainMultipleEntriesAndMultipleValuesWithinEachEntry() {
        RawHttpHeaders.newBuilder()
                .with("hi", "aaa, ccc")
                .with("hi", "bbb")
                .with("ho", "ccc xxx")
                .with("hi", "ddd, eee,fff")
                .build().run {
                    get("hi") shouldEqual listOf("aaa, ccc", "bbb", "ddd, eee,fff")
                    get("hi", ",\\s*") shouldEqual listOf("aaa", "ccc", "bbb", "ddd", "eee", "fff")
                    get("ho") shouldEqual listOf("ccc xxx")
                    get("ho", " ") shouldEqual listOf("ccc", "xxx")

                    headerNames shouldEqual listOf("hi", "hi", "ho", "hi")
                    uniqueHeaderNames shouldEqual setOf("HI", "HO")
                }
    }

    @Test
    fun headersCanBeReConstructedExactly() {
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

    @Test
    fun headersMayBeAddedToOtherHeaders() {
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

    @Test
    fun headersMayBeBuiltFromOtherHeaders() {
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

    @Test
    fun headersMayBeMergedWithOtherHeaders() {
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

    @Test
    fun headerNamesMustNotContainInvalidCharacters() {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder()
                    .with("ABC(D)", "aaa").build()
        }
        error.message shouldEqual "Invalid header name (contains illegal character at index 3)"
    }

    @Test
    fun headerNamesMayContainInvalidCharactersIfSkippingValidation() {
        RawHttpHeaders.newBuilderSkippingValidation().with("ABC(D)", "aaa").build().run {
            headerNames shouldBe listOf("ABC(D)")
            get("ABC(D)") shouldBe listOf("aaa")
        }
    }

    @Test
    fun headerValuesMustNotContainInvalidCharacters() {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder()
                    .with("Hello", "hal\u007Fl").build()
        }
        error.message shouldEqual "Invalid header value (contains illegal character at index 3)"
    }

    @Test
    fun headerValuesMayContainInvalidCharactersIfSkippingValidation() {
        RawHttpHeaders.newBuilderSkippingValidation().with("Hello", "hallå").build().run {
            headerNames shouldBe listOf("Hello")
            get("Hello") shouldBe listOf("hallå")
        }
    }

    @Test
    fun headerBuilderMayRemovePreviousHeaders() {
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

    @Test
    fun headerBuilderMayOverwritePreviousValues() {
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

    @Test
    fun headerNamesAreValidatedByDefault() {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder().with("A:B", "aaa")
        }

        error.message shouldBe "Invalid header name (contains illegal character at index 1)"
    }

    @Test
    fun headerValuesAreValidatedByDefault() {
        val error = shouldThrow<InvalidHttpHeader> {
            RawHttpHeaders.newBuilder().with("A", "abc+\u0000eedd")
        }

        error.message shouldBe "Invalid header value (contains illegal character at index 4)"
    }

    @Test
    fun headerNamesAreNOTValidatedIfAsked() {
        RawHttpHeaders.newBuilderSkippingValidation().with("A:B", "aaa")
    }

    @Test
    fun headerValuesAreNOTValidatedIfAsked() {
        RawHttpHeaders.newBuilderSkippingValidation().with("A", "abc+åäö")
    }

}