package com.athaydes.rawhttp.core.body

import com.athaydes.rawhttp.core.BodyReader
import com.athaydes.rawhttp.core.bePresent
import com.athaydes.rawhttp.core.shouldHaveSameElementsAs
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec

class ChunkedBodyTest : StringSpec({

    "Can encode chunked body with single chunk" {
        val stream = "Hi".byteInputStream()
        val body = ChunkedBody(null, stream, 2)

        body.toBodyReader().eager().should {
            it.bodyType shouldBe BodyReader.BodyType.CHUNKED
            it.asString(Charsets.US_ASCII) shouldEqual "2\r\nHi\r\n0\r\n\r\n"
            it.asChunkedBodyContents() should bePresent {
                it.chunks.size shouldBe 2
                it.chunks[0].data shouldHaveSameElementsAs "Hi".toByteArray()
                it.chunks[1].data.size shouldBe 0 // last chunk
                it.data shouldHaveSameElementsAs "Hi".toByteArray()
                it.trailerHeaders.asMap().size shouldBe 0
                it.asString(Charsets.US_ASCII) shouldEqual "Hi"
            }
        }
    }

    "Can encode chunked body with single chunk (bigger than the data)" {
        val stream = "Hi".byteInputStream()
        val body = ChunkedBody(null, stream, 512)

        body.toBodyReader().eager().should {
            it.bodyType shouldBe BodyReader.BodyType.CHUNKED
            it.asString(Charsets.US_ASCII) shouldEqual "2\r\nHi\r\n0\r\n\r\n"
            it.asChunkedBodyContents() should bePresent {
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
        val body = ChunkedBody(null, stream, 4)

        body.toBodyReader().eager().should {
            it.bodyType shouldBe BodyReader.BodyType.CHUNKED
            it.asString(Charsets.US_ASCII) shouldEqual "4\r\nHell\r\n4\r\no wo\r\n3\r\nrld\r\n0\r\n\r\n"
            it.asChunkedBodyContents() should bePresent {
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