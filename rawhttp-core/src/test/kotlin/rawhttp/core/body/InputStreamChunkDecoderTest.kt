package rawhttp.core.body

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import rawhttp.core.HttpMetadataParser
import rawhttp.core.RawHttpOptions
import rawhttp.core.internal.Bool
import rawhttp.core.shouldHaveSameElementsAs

class InputStreamChunkDecoderTest {

    private val chunkedBodyParser = ChunkedBodyParser(HttpMetadataParser(RawHttpOptions.defaultInstance()))

    @Test
    fun canDecodeEmptyChunkedBody() {
        val chunkedBody = "0\r\n\r\n"
        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        decoderStream.readBytes() shouldHaveSameElementsAs byteArrayOf()
        decoderStream.trailer.asMap() shouldBe mapOf<String, List<String>>()
    }

    @Test
    fun canDecodeChunkedBody() {
        val chunkedBody = "4\r\nHell\r\n4\r\no wo\r\n3\r\nrld\r\n0\r\n\r\n"
        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        decoderStream.readBytes() shouldHaveSameElementsAs "Hello world".toByteArray()
        decoderStream.trailer.asMap() shouldBe mapOf<String, List<String>>()
    }

    @Test
    fun canDecodeLargerChunkedBody() {
        val chunkedBody = "4\r\nWiki\r\n5\r\npedia\r\nE\r\n in\r\n\r\nchunks.\r\n0\r\n\r\n"

        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        decoderStream.readBytes() shouldHaveSameElementsAs "Wikipedia in\r\n\r\nchunks.".toByteArray()
        decoderStream.trailer.asMap() shouldBe mapOf<String, List<String>>()
    }

    @Test // see issue #9
    fun canDecodeChunkedBodyWithTinyChunksLikeGoogleUses() {
        val chunkedBody = "00000001\r\n_\r\n00000001;abc=def\r\n_\r\n" +
                "0000000000000000000000000000000000000000000000000000000000000001\r\n_\r\n" +
                "0004\r\nWiki\r\n0005\r\npedia\r\nE\r\n in\r\n\r\nchunks.\r\n0000\r\n\r\n"

        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        val chunkIterator = decoderStream.asIterator()

        repeat(3) { index ->
            chunkIterator.hasNext() shouldBe true
            chunkIterator.next().let {
                it.data shouldHaveSameElementsAs "_".toByteArray()
                if (index == 1)
                    it.extensions.asMap() shouldBe mapOf("ABC" to listOf("def"))
                else
                    it.extensions.asMap() shouldBe emptyMap<String, Any>()
            }
        }

        decoderStream.readBytes() shouldHaveSameElementsAs "Wikipedia in\r\n\r\nchunks.".toByteArray()
        decoderStream.trailer.asMap() shouldBe emptyMap<String, Any>()
    }

    @Test
    fun canDecodeChunkedBodyWithTrailer() {
        val chunkedBody = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\n"

        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        decoderStream.readBytes() shouldHaveSameElementsAs "98".toByteArray()
        decoderStream.trailer.asMap() shouldBe mapOf(
                "HELLO" to listOf("hi there", "wow"),
                "BYE" to listOf("true"))
    }

    @Test
    @Disabled("Cannot parse extensions without a value") // FIXME
    fun canDecodeChunkedBodyContainingExtensionsAndTrailer() {
        val chunkedBody = """
            4;foo=bar
            evil
            1;foo=bbb
            =
            1;fooxbazonk
            h
            1;zxczxczxc
            a
            1;yadadada
            h
            1;hrrmph
            a""".trimIndent()

        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        decoderStream.readBytes() shouldHaveSameElementsAs "evilhaha".toByteArray()
        decoderStream.trailer.asMap() shouldBe mapOf<String, List<String>>()
    }

    @Test
    fun canReadChunkByChunk() {
        val chunkedBody = "4\r\nHell\r\n4\r\no wo\r\n3;ext=true\r\nrld\r\n0\r\n\r\nIGNORED"
        val originalStream = chunkedBody.byteInputStream()
        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, originalStream)

        val chunk1 = decoderStream.readChunk()
        chunk1.data shouldHaveSameElementsAs "Hell".toByteArray()
        chunk1.size() shouldBe 4
        chunk1.extensions.isEmpty

        val chunk2 = decoderStream.readChunk()
        chunk2.data shouldHaveSameElementsAs "o wo".toByteArray()
        chunk2.size() shouldBe 4
        chunk2.extensions.isEmpty

        val chunk3 = decoderStream.readChunk()
        chunk3.data shouldHaveSameElementsAs "rld".toByteArray()
        chunk3.size() shouldBe 3
        chunk3.extensions.asMap() shouldBe mapOf("EXT" to listOf("true"))

        val lastChunk = decoderStream.readChunk()
        lastChunk.data.asList() should beEmpty()
        lastChunk.size() shouldBe 0

        decoderStream.trailer.asMap() shouldBe mapOf<String, List<String>>()

        shouldThrow<IllegalStateException> { decoderStream.readChunk() }

        originalStream.reader().readText() shouldBe "IGNORED"
    }

    @Test
    fun cannotDecodeInvalidChunkSizeWithTooManyBytes() {
        // 65 bytes long
        val chunkedBody = "00000000000000000000000000000000000000000000000000000000000000001"

        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        shouldThrow<java.lang.IllegalStateException> {
            decoderStream.readChunk()
        }.message shouldBe "Invalid chunk-size (too many digits)"
    }

    @Test
    fun cannotDecodeInvalidChunkSizeTooBig() {
        // too big to fit into an int
        val chunkedBody = "FFFFFFFF"

        val decoderStream = InputStreamChunkDecoder(chunkedBodyParser, chunkedBody.byteInputStream())

        shouldThrow<java.lang.IllegalStateException> {
            decoderStream.readChunk()
        }.message shouldBe "Invalid chunk-size (too big)"
    }

    @Test
    fun chunkSizeIsParsedCorrectly() {
        chunkedBodyParser.readChunkSize("0000000\n".byteInputStream(), Bool()) shouldBe 0
        chunkedBodyParser.readChunkSize("0000001\n".byteInputStream(), Bool()) shouldBe 1
        chunkedBodyParser.readChunkSize("000000A\n".byteInputStream(), Bool()) shouldBe 10
        chunkedBodyParser.readChunkSize("1000000\n".byteInputStream(), Bool()) shouldBe 16_777_216
        chunkedBodyParser.readChunkSize("abcdef\n".byteInputStream(), Bool()) shouldBe 11_259_375

        // largest allowed chunk size
        chunkedBodyParser.readChunkSize("FFFFFFF\n".byteInputStream(), Bool()) shouldBe 268_435_455
        chunkedBodyParser.readChunkSize("00000FFFFFFF\n".byteInputStream(), Bool()) shouldBe 268_435_455
    }
}
