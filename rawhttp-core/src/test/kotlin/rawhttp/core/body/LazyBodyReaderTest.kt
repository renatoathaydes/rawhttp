package rawhttp.core.body

import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import rawhttp.core.HttpMetadataParser
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders
import rawhttp.core.RawHttpOptions
import rawhttp.core.bePresent
import rawhttp.core.body.BodyType.Chunked
import rawhttp.core.body.BodyType.CloseTerminated
import rawhttp.core.body.BodyType.ContentLength
import rawhttp.core.body.encoding.ServiceLoaderHttpBodyEncodingRegistry
import rawhttp.core.shouldHaveSameElementsAs
import java.io.ByteArrayOutputStream
import kotlin.text.Charsets.UTF_8

class LazyBodyReaderTest : StringSpec({

    val metadataParser = HttpMetadataParser(RawHttpOptions.defaultInstance())
    val registry = ServiceLoaderHttpBodyEncodingRegistry()
    val noOpDecoder = BodyDecoder()

    "Can read and write content-length body" {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = LazyBodyReader(ContentLength(body.length.toLong()), stream)

        reader.run {
            bodyType shouldBe ContentLength(body.length.toLong())
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read and write empty content-length body" {
        val body = ""
        val stream = body.byteInputStream()
        val reader = LazyBodyReader(ContentLength(body.length.toLong()), stream)

        reader.run {
            bodyType shouldBe ContentLength(body.length.toLong())
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read and write body until EOF" {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = LazyBodyReader(CloseTerminated(noOpDecoder), stream)

        reader.run {
            bodyType shouldBe CloseTerminated(noOpDecoder)
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read and write simple chunked body" {
        val body = byteArrayOf(56, 13, 10, 72, 105, 32, 116, 104, 101, 114, 101, 13, 10, 48, 13, 10, 13, 10)

        val createReader = {
            val stream = body.inputStream()
            LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser), stream)
        }

        // verify chunks
        createReader().run {
            bodyType shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser)
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
        }

        // verify raw bytes
        createReader().run {
            asBytes() shouldHaveSameElementsAs body
        }

        // verify writing
        createReader().run {
            val writtenBody = ByteArrayOutputStream(body.size)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body
        }
    }

    "Can read chunked body containing metadata" {
        val body = "5;abc=123\r\n12345\r\n2\r\n98\r\n0\r\n\r\n"

        val createReader = {
            val stream = body.toByteArray().inputStream()
            LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser), stream)
        }

        createReader().run {
            bodyType shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser)
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
        }

        // verify raw bytes
        createReader().run {
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }

        // verify writing
        createReader().run {
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    val strictMetadataParser = HttpMetadataParser(RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .build())

    "Can read empty chunked body with only extensions in last chunk" {
        val body = "0;hi=true;hi=22;bye=false,maybe;cool\r\n\r\n"

        val createReader = {
            val stream = body.toByteArray().inputStream()
            LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser), stream)
        }

        createReader().run {
            bodyType shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser)
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
        }

        // verify raw bytes
        createReader().run {
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }

        // verify writing
        createReader().run {
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read chunked body with trailer" {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\nIGNORED"

        val stream = body.toByteArray().inputStream()
        val reader = LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser), stream)

        reader.run {
            bodyType shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser)
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
                        .with("Bye", "true")
                        .with("Hello", "wow")
                        .build()
            }
        }

        // verify that the parser stopped at the correct body ending
        stream.readBytes().toString(UTF_8) shouldEqual "IGNORED"
    }

})