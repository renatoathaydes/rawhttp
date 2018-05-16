package rawhttp.core.body

import org.junit.Test
import rawhttp.core.shouldHaveSameElementsAs


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

}
