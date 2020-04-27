package rawhttp.samples

import rawhttp.core.RawHttp
import rawhttp.core.RawHttpHeaders
import rawhttp.core.RawHttpOptions
import rawhttp.core.RawHttpRequest
import rawhttp.core.RawHttpResponse
import rawhttp.core.RequestLine
import rawhttp.core.body.StringBody
import rawhttp.core.server.Router
import rawhttp.core.server.TcpRawHttpServer
import java.util.Optional
import kotlin.math.min

const val port = 8345

val http = RawHttp(RawHttpOptions.strict())

val okResponse: RawHttpResponse<Void> = http.parseResponse("200 OK HTTP/1.1\r\nContent-Type: text/plain").eagerly()
val badRequestResponse: RawHttpResponse<Void> = http.parseResponse("400 Bad Request HTTP/1.1\r\nContent-Type: text/plain").eagerly()
val notFoundResponse: RawHttpResponse<Void> = http.parseResponse("404 Not Found HTTP/1.1\r\nContent-Type: text/plain").eagerly()
val paymentRequiredResponse: RawHttpResponse<Void> = http.parseResponse("402 Payment Required HTTP/1.1\r\nContent-Type: text/plain").eagerly()
val methodNotAllowedResponse: RawHttpResponse<Void> = http.parseResponse("405 Method Not Allowed\r\nContent-Length: 0").eagerly()

const val maxLengthAllowed = 1_000_000

val server = TcpRawHttpServer(port)

typealias MaybeError = RawHttpResponse<Void>?

fun main() {
    println("Starting server on port $port")

    // a simple HTTP server that accepts POST requests and returns how many words there are in their text body...
    // it only accepts "text/*" Content-Type, and the body must not be longer than 100_000 bytes.
    // clients may use the "Expect: 100-continue" header to find out if they can send their bodies before doing it.
    server.start(object : Router {
        val isValidatedRequest = ThreadLocal.withInitial { false }

        override fun route(request: RawHttpRequest): Optional<RawHttpResponse<*>> {
            if (isValidatedRequest.get()) {
                isValidatedRequest.set(false)
            } else {
                validateRequest(request.startLine, request.headers)?.also { error ->
                    request.eagerly() // consume body in case of error
                    return@route Optional.of(error)
                }
            }
            val body = request.body.map { bodyReader -> bodyReader.decodeBodyToString(Charsets.UTF_8) }.orElse("")
            println("Received body: ${body.substring(0, min(100, body.length))}${if (body.length > 100) "..." else ""}")
            val words = body.split(Regex("\\s+")).filter { it.isNotBlank() }.size
            return Optional.of(okResponse.withBody(StringBody("$words")))
        }

        override fun continueResponse(requestLine: RequestLine, headers: RawHttpHeaders):
                Optional<RawHttpResponse<Void>> {
            println("Client asking if it can continue and send the body for\n$requestLine\n$headers")
            return Optional.ofNullable(validateRequest(requestLine, headers))
        }

        private fun validateRequest(requestLine: RequestLine, headers: RawHttpHeaders): MaybeError {
            if (requestLine.method != "POST") return methodNotAllowedResponse
            if (requestLine.uri.path != "/") return notFoundResponse
                    .withBody(StringBody("Nothing here, only POSTs to '/' are accepted"))

            val isText = headers["Content-Type"].firstOrNull()?.startsWith("text/") ?: false

            return if (isText) {
                val length = headers["Content-Length"].firstOrNull()?.toLongOrNull()
                        ?: return badRequestResponse.withBody(
                                StringBody("Content-Length header must be sent and have a valid value"))
                if (length > maxLengthAllowed)
                    paymentRequiredResponse.withBody(
                            StringBody("Body too large - please pay us money first"))
                else null // no error
            } else {
                badRequestResponse.withBody(StringBody("Request body must be text"))
            }
        }
    })
}