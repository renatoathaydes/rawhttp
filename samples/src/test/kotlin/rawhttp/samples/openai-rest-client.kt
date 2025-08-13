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

/**
 * curl http://localhost:1234/v1/chat/completions \
 *   -H "Content-Type: application/json" \
 *   -d '{
 *     "model": "openai/gpt-oss-20b",
 *     "messages": [
 *       { "role": "system", "content": "Always answer in rhymes. Today is Thursday" },
 *       { "role": "user", "content": "What day is it today?" }
 *     ],
 *     "temperature": 0.7,
 *     "max_tokens": -1,
 *     "stream": false
 * }'
 */
object OpenAIRestClient {

    private enum class Role { system, user, }

    private data class Message(val role: Role, val content: String) {
        fun toMap(): Map<String, Any> = mapOf("role" to role.name, "content" to content)
    }

    private fun List<Message>.toMapList() = map { it.toMap() }

    private data class Request(
        val model: String,
        val messages: List<Message>,
        val temperature: Float = 0.7f,
        val maxTokens: Int = -1,
        val stream: Boolean = true,
    ) {
        fun toJson() = JSONObject(
            mapOf(
                "model" to model,
                "messages" to messages.toMapList(),
                "temperature" to temperature,
                "maxTokens" to maxTokens,
                "stream" to stream,
            )
        ).toString()

        fun toJsonBody() = StringBody(toJson(), "application/json")
    }

    private data class Response(
        val id: String,
        val objectType: String,
        val model: String,
        val createdAt: Instant,
        val choices: List<Choice>,
    ) {
        val finishReason: String? = choices.firstOrNull()?.finishReason
        val refusal: String? = choices.firstOrNull()?.delta?.refusal
        val response: String? = choices.firstOrNull()?.delta?.content

        data class Delta(val content: String?, val refusal: String?) {
            companion object {
                fun fromJson(json: JSONObject): Delta = Delta(
                    content = json.getStringOrNull("content"),
                    refusal = json.getStringOrNull("refusal")
                )
            }
        }

        data class Choice(val delta: Delta, val finishReason: String?, val index: Int)

        companion object {
            /**
             * See https://platform.openai.com/docs/api-reference/chat_streaming/streaming
             */
            fun fromJson(json: String): Response? {
                return try {
                    // LM Studio seems to add this prefix to the JSON objects
                    val actualJson = json.removePrefix("data: ")
                    if (actualJson.startsWith("[DONE]")) return null
                    JSONObject(actualJson).run {
                        val objectType = getString("object")
                        if (objectType != "chat.completion.chunk") {
                            println("Unexpected object type: '$objectType'")
                            return null
                        }
                        Response(
                            id = getString("id"),
                            objectType = objectType,
                            model = getString("model"),
                            createdAt = Instant.ofEpochSecond(getLong("created")),
                            choices = getJSONArray("choices").map { c ->
                                c as JSONObject
                                Choice(
                                    delta = Delta.fromJson(c.getJSONObject("delta")),
                                    finishReason = c.getStringOrNull("finish_reason"),
                                    index = c.getInt("index")
                                )
                            },
                        )
                    }
                } catch (e: Exception) {
                    println("ERROR parsing response, the JSON String was: '$json' ($e)")
                    null
                }
            }
        }
    }

    // Ollama default port: 11434
    // LM Studio: 1234
    // Llamafile: 8080
    const val port = 1234

    val http = RawHttp()
    val client = TcpRawHttpClient(object : TcpRawHttpClient.DefaultOptions() {
        override fun getSocket(uri: URI?): Socket = super.getSocket(uri).apply {
            // the default timeout is only 5s, but a LLM request may take longer
            soTimeout = 60_000
        }
    }, http)

    @JvmStatic
    fun main(args: Array<String>) {
        val startTime = Instant.now()
        client.use {
            println("Sending Ollama Request")
            val request = Request(
                model = "openai/gpt-oss-20b",
                messages = listOf(
                    Message(
                        role = Role.system,
                        content = "You are a helpful assistant. Answer any questions in a friendly, but brief manner."
                    ),
                    Message(
                        role = Role.user,
                        content = "Tell me about the biggest cities in Brazil."
                    ),
                )
            )
            val response = client.send(
                http.parseRequest(
                    "POST http://localhost:$port/v1/chat/completions\r\n" +
                            "Accept: application/json"
                )
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
        println("Done in ${Duration.between(startTime, Instant.now())}")
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
            if (response == null) {
                continue
            }
            val refusal = response.refusal
            if (refusal != null) {
                println("\n\n===============\nREFUSAL: ${response.refusal}\n=================")
            }
            if (response.finishReason != null) {
                println("\n>>>> Stopped, reason: ${response.finishReason} <<<<")
                println(response)
            } else if (refusal == null && response.response != null) {
                print(response.response)
            }
        }
    }

    private fun JSONObject.getStringOrNull(key: String): String? {
        if (isNull(key) || !has(key)) return null
        return getString(key)
    }
}