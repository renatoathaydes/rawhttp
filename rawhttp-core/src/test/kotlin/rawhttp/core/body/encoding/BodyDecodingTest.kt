package rawhttp.core.body.encoding

import io.kotlintest.matchers.shouldBe
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
import java.nio.charset.StandardCharsets
import java.util.Optional

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

        val actualEncodedBody = response.body.map { it.asBytes() }.orElse(ByteArray(0))

        actualEncodedBody shouldHaveSameElementsAs gzippedFileBytes
    }

    @Test
    fun canDecodeGzippedThenChunkedBody() {
        val encodingMap = mapOf("gzip" to GzipDecoder(),
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

        val actualEncodedBody = response.body.map { it.asBytes() }.orElse(ByteArray(0))

        // encode gzipped file with InputStreamChunkEncoder using different chunk sizes to make sure
        // chunked body did encode it correctly
        val chunkedZippedFileBytes = InputStreamChunkEncoder(ByteArrayInputStream(gzippedFileBytes), 4).readBytes()

        actualEncodedBody shouldHaveSameElementsAs chunkedZippedFileBytes
    }

}