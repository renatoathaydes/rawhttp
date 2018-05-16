package rawhttp.core

import io.kotlintest.matchers.beOfType
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders
import rawhttp.core.body.BodyType.Chunked
import rawhttp.core.body.BodyType.CloseTerminated
import rawhttp.core.body.BodyType.ContentLength
import rawhttp.core.body.EagerBodyReader
import kotlin.text.Charsets.UTF_8

class EagerBodyReaderTest : StringSpec({

    "Can read content-length body" {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = EagerBodyReader(ContentLength(body.length.toLong()), stream)

        reader.run {
            bodyType should beOfType<ContentLength>()
            isChunked shouldBe false
            asString(Charsets.UTF_8) shouldBe body
            asChunkedBodyContents() should notBePresent()
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read empty content-length body" {
        val body = ""
        val stream = body.byteInputStream()
        val reader = EagerBodyReader(ContentLength(body.length.toLong()), stream)

        reader.run {
            bodyType should beOfType<ContentLength>()
            isChunked shouldBe false
            asString(Charsets.UTF_8) shouldBe body
            asChunkedBodyContents() should notBePresent()
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read body until EOF" {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = EagerBodyReader(CloseTerminated(emptyList()), stream)

        reader.run {
            bodyType shouldBe CloseTerminated(emptyList())
            isChunked shouldBe false
            asString(Charsets.UTF_8) shouldBe body
            asChunkedBodyContents() should notBePresent()
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    val metadataParser = HttpMetadataParser(RawHttpOptions.defaultInstance())

    "Can read simple chunked body" {
        val body = byteArrayOf(56, 13, 10, 72, 105, 32, 116, 104, 101, 114, 101, 13, 10, 48, 13, 10, 13, 10)

        val stream = body.inputStream()
        val reader = EagerBodyReader(Chunked(listOf("chunked"), metadataParser), stream)

        reader.run {
            bodyType shouldBe Chunked(listOf("chunked"), metadataParser)
            isChunked shouldBe true
            asChunkedBodyContents() should bePresent {
                it.data shouldHaveSameElementsAs "Hi there".toByteArray()
                it.chunks.size shouldBe 2

                it.chunks[0].data shouldHaveSameElementsAs "Hi there".toByteArray()
                it.chunks[0].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[0].size() shouldBe 8

                it.chunks[1].data.size shouldBe 0
                it.chunks[1].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 0

                it.trailerHeaders shouldEqual emptyRawHttpHeaders()
            }
            asBytes() shouldHaveSameElementsAs body
            asString(Charsets.UTF_8) shouldBe "8\r\nHi there\r\n0\r\n\r\n"
            decodeBody() shouldHaveSameElementsAs "Hi there".toByteArray()
            decodeBodyToString(UTF_8) shouldBe "Hi there"
        }
    }

    "Can read chunked body containing metadata" {
        val body = "5;abc=123\r\n12345\r\n2\r\n98\r\n0\r\n\r\n"

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(Chunked(listOf("chunked"), metadataParser), stream)

        reader.run {
            bodyType shouldBe Chunked(listOf("chunked"), metadataParser)
            isChunked shouldBe true
            asChunkedBodyContents() should bePresent {
                it.data shouldHaveSameElementsAs "1234598".toByteArray()
                it.chunks.size shouldBe 3

                it.chunks[0].data shouldHaveSameElementsAs "12345".toByteArray()
                it.chunks[0].extensions shouldEqual RawHttpHeaders.newBuilder()
                        .with("abc", "123").build()
                it.chunks[0].size() shouldBe 5

                it.chunks[1].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[1].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 2

                it.chunks[2].data.size shouldBe 0
                it.chunks[2].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[2].size() shouldBe 0

                it.trailerHeaders shouldEqual emptyRawHttpHeaders()
            }
            asBytes() shouldHaveSameElementsAs body.toByteArray()
            asString(Charsets.UTF_8) shouldBe body
            decodeBody() shouldHaveSameElementsAs "1234598".toByteArray()
            decodeBodyToString(UTF_8) shouldBe "1234598"
        }
    }

    val strictMetadataParser = HttpMetadataParser(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .build())

    "Can read empty chunked body with only extensions in last chunk" {
        val body = "0;hi=true;hi=22;bye=false,maybe;cool\r\n\r\n"

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(Chunked(listOf("chunked"), strictMetadataParser), stream)

        reader.run {
            bodyType shouldBe Chunked(listOf("chunked"), strictMetadataParser)
            isChunked shouldBe true
            asChunkedBodyContents() should bePresent {
                it.data shouldHaveSameElementsAs "".toByteArray()
                it.chunks.size shouldBe 1

                it.chunks[0].data.size shouldBe 0
                it.chunks[0].extensions shouldEqual RawHttpHeaders.newBuilder()
                        .with("hi", "true")
                        .with("hi", "22")
                        .with("bye", "false,maybe")
                        .with("cool", "")
                        .build()
                it.chunks[0].size() shouldBe 0

                it.trailerHeaders shouldEqual emptyRawHttpHeaders()
            }
            asBytes() shouldHaveSameElementsAs body.toByteArray()
            asString(UTF_8) shouldBe body
            decodeBody() shouldHaveSameElementsAs byteArrayOf()
            decodeBodyToString(UTF_8) shouldBe ""
        }
    }

    "Can read chunked body with trailer" {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nHello: wow\r\nBye: true\r\n\r\n"

        // add some extra bytes to the stream so we can test the HTTP message is only read to its last byte
        val stream = (body + "IGNORED").toByteArray().inputStream()
        val reader = EagerBodyReader(Chunked(listOf("chunked"), strictMetadataParser), stream)

        reader.run {
            bodyType shouldBe Chunked(listOf("chunked"), strictMetadataParser)
            isChunked shouldBe true
            asChunkedBodyContents() should bePresent {
                it.data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks.size shouldBe 2

                it.chunks[0].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[0].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[0].size() shouldBe 2

                it.chunks[1].data.size shouldBe 0
                it.chunks[1].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 0

                it.trailerHeaders shouldEqual RawHttpHeaders.newBuilder()
                        .with("Hello", "hi there")
                        .with("Hello", "wow")
                        .with("Bye", "true")
                        .build()
            }
            asBytes() shouldHaveSameElementsAs body.toByteArray()
            asString(Charsets.UTF_8) shouldBe body
            decodeBody() shouldHaveSameElementsAs "98".toByteArray()
            decodeBodyToString(UTF_8) shouldBe "98"
        }

        // verify that the parser stopped at the correct body ending
        stream.readBytes().toString(UTF_8) shouldEqual "IGNORED"
    }

})