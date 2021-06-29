package rawhttp.samples

import rawhttp.core.RawHttp
import rawhttp.core.body.StringBody
import rawhttp.core.server.TcpRawHttpServer
import java.util.Optional

private val rawHttp = RawHttp()

val basicResponse = rawHttp.parseResponse("""
HTTP/1.1 200 OK
Content-Type: text/plain
""".trimIndent())

fun main() {
    val server = TcpRawHttpServer(8081)
    server.start { request ->
        Optional.of(basicResponse.withBody(StringBody(request.toString())))
    }
}
