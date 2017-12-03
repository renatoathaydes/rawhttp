package com.athaydes.rawhttp.core

import com.athaydes.rawhttp.core.BodyReader.BodyType.CHUNKED
import com.athaydes.rawhttp.core.BodyReader.BodyType.CLOSE_TERMINATED
import com.athaydes.rawhttp.core.BodyReader.BodyType.CONTENT_LENGTH
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

    "Can read chunked body" {
        val body = arrayOf<Byte>(
                // chunk size = 5
                5,
                // abc=123
                97, 98, 99, 61, 49, 50, 51,
                // \r\n
                13, 10,
                // 12345
                49, 50, 51, 52, 53,
                // chunk size = 2
                2,
                // \r\n
                13, 10,
                // 98
                57, 56,
                // chunk size = 0 + \r\n\r\n
                0, 13, 10, 13, 10)

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe "1234598"
            asChunkedBody() should bePresent {
                it.data shouldHaveSameElementsAs "1234598".toByteArray()
                it.chunks.size shouldBe 3

                it.chunks[0].data shouldHaveSameElementsAs "12345".toByteArray()
                it.chunks[0].extensions shouldEqual mapOf("abc" to listOf("123"))
                it.chunks[0].size() shouldBe 5

                it.chunks[1].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[1].extensions shouldEqual emptyMap<String, Collection<String>>()
                it.chunks[1].size() shouldBe 2

                it.chunks[2].data.size shouldBe 0
                it.chunks[2].extensions shouldEqual emptyMap<String, Collection<String>>()
                it.chunks[2].size() shouldBe 0

                it.trailerHeaders shouldEqual emptyMap<String, Collection<String>>()
            }
            asBytes() shouldHaveSameElementsAs "1234598".toByteArray()
        }
    }

    "Can read empty chunked body with only extensions in last chunk" {
        val body = arrayOf(
                // chunk size = 0 + METADATA + \r\n\r\n
                0, *("hi=true;bye=false,maybe;hi=22;cool").toByteArray().toTypedArray(), 13, 10, 13, 10)

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe ""
            asChunkedBody() should bePresent {
                it.data shouldHaveSameElementsAs "".toByteArray()
                it.chunks.size shouldBe 1

                it.chunks[0].data.size shouldBe 0
                it.chunks[0].extensions shouldEqual mapOf(
                        "hi" to listOf("true", "22"),
                        "bye" to listOf("false,maybe"),
                        "cool" to listOf(""))
                it.chunks[0].size() shouldBe 0

                it.trailerHeaders shouldEqual emptyMap<String, Collection<String>>()
            }
            asBytes() shouldHaveSameElementsAs "".toByteArray()
        }
    }

    "Can read chunked body with trailer" {
        val body = arrayOf(
                // chunk size = 2
                2,
                // \r\n
                13, 10,
                // 98
                57, 56,
                // chunk size = 0 + \r\n
                0, 13, 10,
                // trailer
                *("Hello: hi there\r\nBye:true\r\nHello: wow\r\n\r\nIGNORED".toByteArray()).toTypedArray()
        )

        val stream = body.toByteArray().inputStream()
        val reader = EagerBodyReader(CHUNKED, stream, null)

        reader.run {
            bodyType shouldBe CHUNKED
            asString(Charsets.UTF_8) shouldBe "98"
            asChunkedBody() should bePresent {
                it.data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks.size shouldBe 2

                it.chunks[0].data shouldHaveSameElementsAs "98".toByteArray()
                it.chunks[0].extensions shouldEqual emptyMap<String, Collection<String>>()
                it.chunks[0].size() shouldBe 2

                it.chunks[1].data.size shouldBe 0
                it.chunks[1].extensions shouldEqual emptyMap<String, Collection<String>>()
                it.chunks[1].size() shouldBe 0

                it.trailerHeaders shouldEqual mapOf(
                        "Hello" to listOf("hi there", "wow"),
                        "Bye" to listOf("true"))
            }
            asBytes() shouldHaveSameElementsAs "98".toByteArray()
        }

        // verify that the parser stopped at the correct body ending
        stream.readBytes().toString(UTF_8) shouldEqual "IGNORED"
    }

})