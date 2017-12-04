package com.athaydes.rawhttp.core

import com.athaydes.rawhttp.core.BodyReader.BodyType.CHUNKED
import com.athaydes.rawhttp.core.BodyReader.BodyType.CLOSE_TERMINATED
import com.athaydes.rawhttp.core.BodyReader.BodyType.CONTENT_LENGTH
import com.athaydes.rawhttp.core.RawHttpHeaders.Builder.emptyRawHttpHeaders
import io.kotlintest.matchers.should
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldEqual
import io.kotlintest.specs.StringSpec
import kotlin.text.Charsets.UTF_8

class EagerBodyReaderTest : StringSpec({

    "Can read content-length body" {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = EagerBodyReader(CONTENT_LENGTH, stream, body.length.toLong())

        reader.run {
            bodyType shouldBe CONTENT_LENGTH
            asString(Charsets.UTF_8) shouldBe body
            asChunkedBody() should notBePresent()
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read empty content-length body" {
        val body = ""
        val stream = body.byteInputStream()
        val reader = EagerBodyReader(CONTENT_LENGTH, stream, body.length.toLong())

        reader.run {
            bodyType shouldBe CONTENT_LENGTH
            asString(Charsets.UTF_8) shouldBe body
            asChunkedBody() should notBePresent()
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read body until EOF" {
        val body = "Hello world"
        val stream = body.byteInputStream()
        val reader = EagerBodyReader(CLOSE_TERMINATED, stream, null)

        reader.run {
            bodyType shouldBe CLOSE_TERMINATED
            asString(Charsets.UTF_8) shouldBe body
            asChunkedBody() should notBePresent()
            asBytes() shouldHaveSameElementsAs body.toByteArray()
        }
    }

    "Can read simple chunked body" {
        val body = byteArrayOf(56, 13, 10, 72, 105, 32, 116, 104, 101, 114, 101, 13, 10, 48, 13, 10, 13, 10)

        val stream = body.inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe "Hi there"
            asChunkedBody() should bePresent {
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
            asBytes() shouldHaveSameElementsAs "Hi there".toByteArray()
        }
    }

    "Can read chunked body containing metadata" {
        val body = "5;abc=123\r\n12345\r\n2\r\n98\r\n0\r\n\r\n"

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe "1234598"
            asChunkedBody() should bePresent {
                it.data shouldHaveSameElementsAs "1234598".toByteArray()
                it.chunks.size shouldBe 3

                it.chunks[0].data shouldHaveSameElementsAs "12345".toByteArray()
                it.chunks[0].extensions shouldEqual RawHttpHeaders.Builder
                        .newBuilder()
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
            asBytes() shouldHaveSameElementsAs "1234598".toByteArray()
        }
    }

    "Can read empty chunked body with only extensions in last chunk" {
        val body = "0;hi=true;bye=false,maybe;hi=22;cool\r\n\r\n"

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe ""
            asChunkedBody() should bePresent {
                it.data shouldHaveSameElementsAs "".toByteArray()
                it.chunks.size shouldBe 1

                it.chunks[0].data.size shouldBe 0
                it.chunks[0].extensions shouldEqual RawHttpHeaders.Builder.newBuilder()
                        .with("hi", "true")
                        .with("hi", "22")
                        .with("bye", "false,maybe")
                        .with("cool", "")
                        .build()
                it.chunks[0].size() shouldBe 0

                it.trailerHeaders shouldEqual emptyRawHttpHeaders()
            }
            asBytes() shouldHaveSameElementsAs "".toByteArray()
        }
    }

    "Can read chunked body with trailer" {
        val body = "2\r\n98\r\n0\r\nHello: hi there\r\nBye:true\r\nHello: wow\r\n\r\nIGNORED"

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe "98"
            asChunkedBody() should bePresent {
                it.data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks.size shouldBe 2

                it.chunks[0].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[0].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[0].size() shouldBe 2

                it.chunks[1].data.size shouldBe 0
                it.chunks[1].extensions shouldEqual emptyRawHttpHeaders()
                it.chunks[1].size() shouldBe 0

                it.trailerHeaders shouldEqual RawHttpHeaders.Builder.newBuilder()
                        .with("Hello", "hi there")
                        .with("Bye", "true")
                        .with("Hello", "wow")
                        .build()
            }
            asBytes() shouldHaveSameElementsAs "98".toByteArray()
        }

        // verify that the parser stopped at the correct body ending
        stream.readBytes().toString(UTF_8) shouldEqual "IGNORED"
    }

})