package rawhttp.core.body

import org.junit.jupiter.api.Test
import rawhttp.core.shouldHaveSameElementsAs
import java.io.ByteArrayInputStream


class InputStreamChunkEncoderTest {

    @Test
    fun canEncodeEmptyChunkedBody() {
        val chunkedBody = ""
        val decoderStream = InputStreamChunkEncoder(chunkedBody.byteInputStream(), 4)

        decoderStream.readBytes() shouldHaveSameElementsAs "0\r\n\r\n".toByteArray()
    }

    @Test
    fun canEncodeChunkedBody() {
        val chunkedBody = "Hello world"
        val decoderStream = InputStreamChunkEncoder(chunkedBody.byteInputStream(), 4)

        decoderStream.readBytes() shouldHaveSameElementsAs "4\r\nHell\r\n4\r\no wo\r\n3\r\nrld\r\n0\r\n\r\n".toByteArray()
    }

    @Test
    fun canEncodeChunkedBodyWithBytes() {
        val bytes = byteArrayOf(31, -117, 8, 8, 25, -61, -1, 90, 0, 3, 103)
        val decoderStream = InputStreamChunkEncoder(ByteArrayInputStream(bytes), 8)

        val expectedChunkedBody = byteArrayOf(
                56, 13, 10, 31, -117, 8, 8, 25, -61, -1, 90, 13, 10,
                51, 13, 10, 0, 3, 103, 13, 10,
                48, 13, 10, 13, 10)

        decoderStream.readBytes() shouldHaveSameElementsAs expectedChunkedBody
    }

}
