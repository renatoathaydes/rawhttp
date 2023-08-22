package rawhttp.core.body

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import rawhttp.core.HttpMetadataParser
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders
import rawhttp.core.RawHttpOptions
import rawhttp.core.body.FramedBody.Chunked
import rawhttp.core.body.FramedBody.CloseTerminated
import rawhttp.core.body.FramedBody.ContentLength
import rawhttp.core.body.encoding.ServiceLoaderHttpBodyEncodingRegistry
import rawhttp.core.shouldHaveSameElementsAs
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.text.Charsets.UTF_8

class LazyBodyReaderTest {

    val metadataParser = HttpMetadataParser(RawHttpOptions.defaultInstance())
    val registry = ServiceLoaderHttpBodyEncodingRegistry()
    val noOpDecoder = BodyDecoder()

    @Test
    fun `Can read and write content-length body`() {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = LazyBodyReader(ContentLength(body.length.toLong()), stream)

        reader.run {
            framedBody shouldBe ContentLength(body.length.toLong())
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    @Test
    fun `Can read and write empty content-length body`() {
        val body = ""
        val stream = body.byteInputStream()
        val reader = LazyBodyReader(ContentLength(body.length.toLong()), stream)

        reader.run {
            framedBody shouldBe ContentLength(body.length.toLong())
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    @Test
    fun `Can read and write body until EOF`() {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = LazyBodyReader(CloseTerminated(noOpDecoder), stream)

        reader.run {
            framedBody shouldBe CloseTerminated(noOpDecoder)
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    @Test
    fun `Can read and write simple chunked body`() {
        val body = byteArrayOf(56, 13, 10, 72, 105, 32, 116, 104, 101, 114, 101, 13, 10, 48, 13, 10, 13, 10)

        val createReader = {
            val stream = body.inputStream()
            LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser), stream)
        }

        // verify chunks
        createReader().run {
            framedBody shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser)
            asChunkedBodyContents() shouldBePresent {
                it.data shouldHaveSameElementsAs "Hi there".toByteArray()
                it.chunks.size shouldBe 2

                it.chunks[0].data shouldHaveSameElementsAs "Hi there".toByteArray()
                it.chunks[0].extensions shouldBe emptyRawHttpHeaders()
                it.chunks[0].size() shouldBe 8

                it.chunks[1].data.size shouldBe 0
                it.chunks[1].extensions shouldBe emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 0

                it.trailerHeaders shouldBe emptyRawHttpHeaders()
            }
        }

        // verify raw bytes
        createReader().run {
            asRawBytes() shouldHaveSameElementsAs body
        }

        // verify writing
        createReader().run {
            val writtenBody = ByteArrayOutputStream(body.size)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body
        }
    }

    @Test
    fun `Can read chunked body containing metadata`() {
        val body = "5;abc=123\r\n12345\r\n2\r\n98\r\n0\r\n\r\n"

        val createReader = {
            val stream = body.toByteArray().inputStream()
            LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser), stream)
        }

        createReader().run {
            framedBody shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), metadataParser)
            asChunkedBodyContents() shouldBePresent {
                it.data shouldHaveSameElementsAs "1234598".toByteArray()
                it.chunks.size shouldBe 3

                it.chunks[0].data shouldHaveSameElementsAs "12345".toByteArray()
                it.chunks[0].extensions shouldBe RawHttpHeaders.newBuilder()
                    .with("abc", "123").build()
                it.chunks[0].size() shouldBe 5

                it.chunks[1].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[1].extensions shouldBe emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 2

                it.chunks[2].data.size shouldBe 0
                it.chunks[2].extensions shouldBe emptyRawHttpHeaders()
                it.chunks[2].size() shouldBe 0

                it.trailerHeaders shouldBe emptyRawHttpHeaders()
            }
        }

        // verify raw bytes
        createReader().run {
            asRawBytes() shouldHaveSameElementsAs body.toByteArray()
        }

        // verify writing
        createReader().run {
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    val strictMetadataParser = HttpMetadataParser(
        RawHttpOptions.newBuilder()
            .doNotAllowNewLineWithoutReturn()
            .build()
    )

    @Test
    fun `Can read empty chunked body with only extensions in last chunk`() {
        val body = "0;hi=true;hi=22;bye=false,maybe;cool\r\n\r\n"

        val createReader = {
            val stream = body.toByteArray().inputStream()
            LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser), stream)
        }

        createReader().run {
            framedBody shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser)
            asChunkedBodyContents() shouldBePresent {
                it.data shouldHaveSameElementsAs "".toByteArray()
                it.chunks.size shouldBe 1

                it.chunks[0].data.size shouldBe 0
                it.chunks[0].extensions shouldBe RawHttpHeaders.newBuilder()
                    .with("hi", "true")
                    .with("hi", "22")
                    .with("bye", "false,maybe")
                    .with("cool", "")
                    .build()
                it.chunks[0].size() shouldBe 0

                it.trailerHeaders shouldBe emptyRawHttpHeaders()
            }
        }

        // verify raw bytes
        createReader().run {
            asRawBytes() shouldHaveSameElementsAs body.toByteArray()
        }

        // verify writing
        createReader().run {
            val writtenBody = ByteArrayOutputStream(body.length)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    @Test
    fun `Can read chunked body with trailer`() {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\nIGNORED"

        val stream = body.toByteArray().inputStream()
        val reader = LazyBodyReader(Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser), stream)

        reader.run {
            framedBody shouldBe Chunked(BodyDecoder(registry, listOf("chunked")), strictMetadataParser)
            asChunkedBodyContents() shouldBePresent {
                it.data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks.size shouldBe 2

                it.chunks[0].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[0].extensions shouldBe emptyRawHttpHeaders()
                it.chunks[0].size() shouldBe 2

                it.chunks[1].data.size shouldBe 0
                it.chunks[1].extensions shouldBe emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 0

                it.trailerHeaders shouldBe RawHttpHeaders.newBuilder()
                    .with("Hello", "hi there")
                    .with("Bye", "true")
                    .with("Hello", "wow")
                    .build()
            }
        }

        // verify that the parser stopped at the correct body ending
        stream.readBytes().toString(UTF_8) shouldBe "IGNORED"
    }

    @Test
    fun `Fails if content-length body does not match full body`() {
        val body = "Short body"
        val stream = body.byteInputStream()
        // the body has only 10 chars, but we say it has 14
        val reader = LazyBodyReader(ContentLength(14), stream)
        val error = shouldThrow<IOException> {
            reader.writeTo(ByteArrayOutputStream(14))
        }
        error.message shouldBe "InputStream provided 10 byte(s), but 14 were expected"
    }

    @Test
    fun `Does not fail if content-length body does not match full body but allowed`() {
        val body = "Short body"
        val stream = body.byteInputStream()
        // the body has only 10 chars, but we say it has 14
        val reader = LazyBodyReader(ContentLength(14, true), stream)
        reader.run {
            val writtenBody = ByteArrayOutputStream(14)
            writeTo(writtenBody)
            writtenBody.toByteArray() shouldHaveSameElementsAs body.toByteArray()
        }
    }

}
