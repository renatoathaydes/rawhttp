package rawhttp.core.body.encoding

import io.kotlintest.matchers.beOfType
import io.kotlintest.matchers.shouldBe
import io.kotlintest.matchers.shouldThrow
import org.junit.Test
import rawhttp.core.HttpMetadataParser
import rawhttp.core.RawHttp
import rawhttp.core.RawHttpOptions
import rawhttp.core.body.BodyDecoder
import rawhttp.core.body.BytesBody
import rawhttp.core.body.ChunkedBody
import rawhttp.core.body.InputStreamChunkEncoder
import rawhttp.core.shouldHaveSameElementsAs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Optional
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipException
import kotlin.test.assertNotNull

class BodyDecodingTest {

    @Test
    fun canDecodeGzippedBody() {
        val encodingMap = mapOf("gzip" to GzipDecoder())
        val bodyDecoder = BodyDecoder({ enc -> Optional.ofNullable(encodingMap[enc]) }, listOf("gzip"))
        val gzippedFileBytes = BodyDecodingTest::class.java.getResource("gzipped-file.txt.gz").readBytes()

        val response = RawHttp().parseResponse("200 OK")
                .withBody(BytesBody(gzippedFileBytes, "text/plain", bodyDecoder)).eagerly()

        val actualDecodedBody = response.body.map {
            it.decodeBodyToString(StandardCharsets.UTF_8)
        }.orElse("NO BODY")

        actualDecodedBody shouldBe "This file should be compressed with GZip.\n"

        val actualEncodedBody = response.body.map { it.asRawBytes() }.orElse(ByteArray(0))

        actualEncodedBody shouldHaveSameElementsAs gzippedFileBytes
    }

    @Test
    fun canDecodeGzippedThenChunkedBody() {
        val encodingMap = mapOf(
                "gzip" to GzipDecoder(),
                "chunked" to ChunkDecoder())
        val bodyDecoder = BodyDecoder({ enc -> Optional.ofNullable(encodingMap[enc]) }, listOf("gzip", "chunked"))
        val gzippedFileBytes = BodyDecodingTest::class.java.getResource("gzipped-file.txt.gz").readBytes()
        val metadataParser = HttpMetadataParser(RawHttpOptions.defaultInstance())

        val response = RawHttp().parseResponse("200 OK").withBody(
                ChunkedBody(ByteArrayInputStream(gzippedFileBytes), null, 8, bodyDecoder, metadataParser)
        ).eagerly()

        val actualDecodedBody = response.body.map {
            it.decodeBodyToString(StandardCharsets.UTF_8)
        }.orElse("NO BODY")

        actualDecodedBody shouldBe "This file should be compressed with GZip.\n"

        val actualEncodedBody = response.body.map { it.asRawBytes() }.orElse(ByteArray(0))

        val chunkedZippedFileBytes = InputStreamChunkEncoder(ByteArrayInputStream(gzippedFileBytes), 8).readBytes()

        actualEncodedBody shouldHaveSameElementsAs chunkedZippedFileBytes
    }

    @Test
    fun canDecodeChunkedBodyThenGzipped() {
        val encodingMap = mapOf(
                "gzip" to GzipDecoder(),
                "chunked" to ChunkDecoder())
        val plainTextBody = "This is the plain text body of a file which will be chunked then gzipped\n".repeat(10)
        val chunkedThenGzippedBodyBytes = ByteArrayOutputStream().run {
            GZIPOutputStream(this).use {
                it.write(InputStreamChunkEncoder(ByteArrayInputStream(plainTextBody.toByteArray()), 12).readBytes())
            }
            toByteArray()
        }

        val bodyDecoder = BodyDecoder({ enc -> Optional.ofNullable(encodingMap[enc]) }, listOf("chunked", "gzip"))

        val response = RawHttp().parseResponse("200 OK").withBody(
                BytesBody(chunkedThenGzippedBodyBytes, null, bodyDecoder)
        ).eagerly()

        val actualDecodedBody = response.body.map {
            it.decodeBodyToString(StandardCharsets.UTF_8)
        }.orElse("NO BODY")

        actualDecodedBody shouldBe plainTextBody

        val actualEncodedBody = response.body.map { it.asRawBytes() }.orElse(ByteArray(0))

        actualEncodedBody shouldHaveSameElementsAs chunkedThenGzippedBodyBytes
    }

    @Test
    fun decodeRealHttpResponseWithChunkedTransferEncodingAndGzipContentEncoding() {
        val plainTextBody = BodyDecodingTest::class.java.getResource("decoded-body.json").readText()

        val response = RawHttp().parseResponse(
                BodyDecodingTest::class.java.getResourceAsStream("chunked-and-gzipped-response.http"))

        response.statusCode shouldBe 200
        response.headers["Transfer-Encoding"] shouldBe listOf("chunked")
        response.headers["Content-Encoding"] shouldBe listOf("gzip")

        val actualDecodedBody = response.body.map {
            it.decodeBodyToString(StandardCharsets.UTF_8)
        }.orElse("NO BODY")

        actualDecodedBody shouldBe plainTextBody
    }

    @Test
    fun identityEncodingIsIgnored() {
        val response = RawHttp().parseResponse("200 OK\nContent-Encoding: identity\nContent-Length: 5\n\nhello")

        response.headers["Content-Encoding"] shouldBe listOf("identity")

        val actualDecodedBody = response.body.map {
            it.decodeBodyToString(StandardCharsets.UTF_8)
        }.orElse("NO BODY")

        actualDecodedBody shouldBe "hello"
    }

    @Test(timeout = 2000L)
    fun corruptedChunkedGzippedBodyDoesNotHang() {
        val gzippedFileStream = BodyDecodingTest::class.java.getResourceAsStream("corrupted-chunked-and-gzipped-response.http")
        val response = RawHttp().parseResponse(gzippedFileStream)
        val body = response.body.get()
        val error = shouldThrow<IOException> { body.decodeBodyToString(StandardCharsets.UTF_8) }
        assertNotNull(error.message)
        error.message shouldBe "java.util.zip.ZipException: Not in GZIP format"
        assertNotNull(error.cause)
        error.cause shouldBe beOfType<ZipException>()
    }

    @Test(timeout = 2000L)
    fun tryingToDecodeCorruptedGzippedBodyGeneratesException() {
        val gzippedFileStream = BodyDecodingTest::class.java.getResourceAsStream("corrupted-gzipped-response.http")
        val response = RawHttp().parseResponse(gzippedFileStream)
        val body = response.body.get()
        val error = shouldThrow<IOException> { body.decodeBodyToString(StandardCharsets.UTF_8) }
        assertNotNull(error.message)
        error.message shouldBe "java.util.zip.ZipException: Not in GZIP format"
        assertNotNull(error.cause)
        error.cause shouldBe beOfType<ZipException>()
    }
}
