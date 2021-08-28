package rawhttp.core.body

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.optional.shouldBePresent
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.beOfType
import rawhttp.core.shouldHaveSameElementsAs

class ChunkedBodyTest : StringSpec({

    "Can encode chunked body with single chunk" {
        val stream = "Hi".byteInputStream()
        val body = ChunkedBody(stream, null, 2)

        body.toBodyReader().eager().should {
            it.framedBody should beOfType<FramedBody.Chunked>()
            it.isChunked shouldBe true
            it.asRawString(Charsets.US_ASCII) shouldBe "2\r\nHi\r\n0\r\n\r\n"
            it.asChunkedBodyContents() shouldBePresent {
                it.chunks.size shouldBe 2
                it.chunks[0].data shouldHaveSameElementsAs "Hi".toByteArray()
                it.chunks[1].data.size shouldBe 0 // last chunk
                it.data shouldHaveSameElementsAs "Hi".toByteArray()
                it.trailerHeaders.asMap().size shouldBe 0
                it.asString(Charsets.US_ASCII) shouldBe "Hi"
            }
        }
    }

    "Can encode chunked body with single chunk (bigger than the data)" {
        val stream = "Hi".byteInputStream()
        val body = ChunkedBody(stream, null, 512)

        body.toBodyReader().eager().should {
            it.framedBody should beOfType<FramedBody.Chunked>()
            it.isChunked shouldBe true
            it.asRawString(Charsets.US_ASCII) shouldBe "2\r\nHi\r\n0\r\n\r\n"
            it.asChunkedBodyContents() shouldBePresent {
                it.chunks.size shouldBe 2
                it.chunks[0].data shouldHaveSameElementsAs "Hi".toByteArray()
                it.chunks[1].data.size shouldBe 0 // last chunk
                it.data shouldHaveSameElementsAs "Hi".toByteArray()
                it.trailerHeaders.asMap().size shouldBe 0
            }
        }
    }

    "Can encode chunked body with several chunks" {
        val stream = "Hello world".byteInputStream()
        val body = ChunkedBody(stream, null, 4)

        body.toBodyReader().eager().should {
            it.framedBody should beOfType<FramedBody.Chunked>()
            it.isChunked shouldBe true
            it.asRawString(Charsets.US_ASCII) shouldBe "4\r\nHell\r\n4\r\no wo\r\n3\r\nrld\r\n0\r\n\r\n"
            it.asChunkedBodyContents() shouldBePresent {
                it.chunks.size shouldBe 4
                it.chunks[0].data shouldHaveSameElementsAs "Hell".toByteArray()
                it.chunks[1].data shouldHaveSameElementsAs "o wo".toByteArray()
                it.chunks[2].data shouldHaveSameElementsAs "rld".toByteArray()
                it.chunks[3].data.size shouldBe 0 // last chunk
                it.data shouldHaveSameElementsAs "Hello world".toByteArray()
                it.trailerHeaders.asMap().size shouldBe 0
            }
        }
    }


})