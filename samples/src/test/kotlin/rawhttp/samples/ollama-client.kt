package rawhttp.samples

import org.json.JSONObject
import rawhttp.core.IOFunction
import rawhttp.core.RawHttp
import rawhttp.core.body.BodyReader
import rawhttp.core.body.ChunkedBodyContents
import rawhttp.core.body.FramedBody
import rawhttp.core.body.StringBody
import rawhttp.core.client.TcpRawHttpClient
import java.net.Socket
import java.net.URI
import java.time.Duration
import java.time.Instant

object OllamaClient {

    const val port = 11434

    val http = RawHttp()
    val client = TcpRawHttpClient(object : TcpRawHttpClient.DefaultOptions() {
        override fun getSocket(uri: URI?): Socket = super.getSocket(uri).apply {
            // the default timeout is only 5s, but a LLM request may take longer
            soTimeout = 60_000
        }
    }, http)

    @JvmStatic
    fun main(args: Array<String>) {
        client.use {
            println("Sending Ollama Request")
            val request = Request(
                model = "llama3_1_8b",
                systemPrompt = "You are a helpful assistant. Answer any questions in a friendly, but brief manner.",
                prompt = "Tell me about football teams in Brazil."
            )
            val response = client.send(
                http.parseRequest("POST http://localhost:$port/api/generate")
                    .withBody(request.toJsonBody())
            )
            println(response.startLine)
            println(response.headers)
            println()
            response.body.ifPresent { body ->
                body.framedBody.use(
                    printBodyCallback(body),
                    { chunked ->
                        consumeStreamingResponse(body, chunked)
                    }, printBodyCallback(body)
                )
            }
        }
        println("Done")
    }

    private fun printBodyCallback(body: BodyReader): IOFunction<Any, Unit> {
        return IOFunction { _ ->
            body.writeTo(System.out)
        }
    }

    private fun consumeStreamingResponse(
        body: BodyReader,
        chunked: FramedBody.Chunked
    ) {
        val inputStream = body.asRawStream()
        var chunk: ChunkedBodyContents.Chunk
        while (true) {
            chunk = chunked.bodyParser.readNextChunk(inputStream)
            if (chunk.size() == 0) break
            val response = Response.fromJson(String(chunk.data, Charsets.UTF_8))
            if (response.done) {
                println()
                println(response)
            } else {
                print(response.response)
            }
        }
    }

    private data class Request(
        val model: String,
        val prompt: String,
        val systemPrompt: String? = null,
    ) {
        fun toJson() = JSONObject(
            mapOf(
                "model" to model,
                "prompt" to prompt,
                "system" to systemPrompt,
            )
        ).toString()

        fun toJsonBody() = StringBody(toJson(), "application/json")
    }

    private data class Response(
        val model: String,
        val createdAt: Instant,
        val response: String,
        val done: Boolean,
        val doneReason: String? = null,
        val totalDuration: String? = null,
        val loadDuration: String? = null,
        val promptEvalCount: Int? = null,
        val promptEvalDuration: String? = null,
        val evalCount: Int? = null,
        val evalDuration: String? = null,
    ) {
        companion object {
            fun fromJson(json: String): Response {
                return JSONObject(json).run {
                    val done = getBoolean("done")
                    val reason = if (done) getString("done_reason") else null
                    val totalDuration = if (done) getDouble("total_duration") else null
                    val loadDuration = if (done) getDouble("load_duration") else null
                    val promptEvalCount = if (done) getInt("prompt_eval_count") else null
                    val promptEvalDuration = if (done) getDouble("prompt_eval_duration") else null
                    val evalCount = if (done) getInt("eval_count") else null
                    val evalDuration = if (done) getDouble("eval_duration") else null

                    Response(
                        getString("model"),
                        Instant.parse(getString("created_at")),
                        getString("response"),
                        done,
                        doneReason = reason,
                        totalDuration = totalDuration.durationText(),
                        loadDuration = loadDuration.durationText(),
                        promptEvalCount = promptEvalCount,
                        promptEvalDuration = promptEvalDuration.durationText(),
                        evalCount = evalCount,
                        evalDuration = evalDuration.durationText()
                    )
                }
            }
        }
    }

    private fun Double?.durationText(): String? {
        if (this == null) return null
        val duration = Duration.ofNanos(toLong())
        return "${duration.seconds}s${duration.toMillisPart()}ms"
    }
}