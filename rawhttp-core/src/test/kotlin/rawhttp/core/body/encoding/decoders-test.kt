package rawhttp.core.body.encoding

import org.junit.Test
import rawhttp.core.body.InputStreamChunkEncoder
import rawhttp.core.shouldHaveSameElementsAs
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.DeflaterInputStream
import java.util.zip.GZIPOutputStream


class DeflateDecoderTest {

    @Test
    fun canDecode() {
        val input = "Hello inflater encoding!".repeat(10)
        val resultOutput = ByteArrayOutputStream()
        DeflaterInputStream(ByteArrayInputStream(input.toByteArray())).apply {
            val decoder = DeflateDecoder()
            val decoderOutputStream = decoder.decode(resultOutput)

            // copy the deflated contents into the decoder output stream, which should inflate it and write
            copyTo(decoderOutputStream)
        }

        resultOutput.toByteArray() shouldHaveSameElementsAs input.toByteArray()
    }
}

class GZipDecoderTest {

    @Test
    fun canDecode() {
        val input = "Hello GZIP encoding!".repeat(10)
        val resultOutput = ByteArrayOutputStream()

        val compressedOutput = ByteArrayOutputStream()
        GZIPOutputStream(compressedOutput).use {
            it.write(input.toByteArray())
        }

        ByteArrayInputStream(compressedOutput.toByteArray()).use { compressed ->
            val decoder = GzipDecoder().apply { setBufferSize(32) }
            decoder.decode(resultOutput).use { decodingStream ->
                compressed.copyTo(decodingStream)
            }
        }

        resultOutput.toByteArray() shouldHaveSameElementsAs input.toByteArray()
    }

}

class ChunkDecoderTest {

    @Test
    fun canDecode() {
        val input = "Hello Chunked encoding!".repeat(10)
        val resultOutput = ByteArrayOutputStream()

        InputStreamChunkEncoder(input.byteInputStream(), 16).use { chunked ->
            val decoder = ChunkDecoder()
            decoder.decode(resultOutput).use { decodingStream ->
                chunked.copyTo(decodingStream)
            }
        }

        resultOutput.toByteArray() shouldHaveSameElementsAs input.toByteArray()
    }

}
