package com.hamyareman.english

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import java.io.ByteArrayInputStream

class LocalTtsServer(
    private val engine: TtsEngine,
    port: Int = 8766
) : NanoHTTPD("127.0.0.1", port) {

    override fun serve(session: IHTTPSession): Response {
        if (session.method == Method.OPTIONS) {
            return cors(newFixedLengthResponse(Response.Status.NO_CONTENT, MIME_PLAINTEXT, ""))
        }

        return try {
            when {
                session.method == Method.GET && session.uri == "/health" ->
                    json(Response.Status.OK, JSONObject(mapOf(
                        "status" to "ok",
                        "engine" to "kokoro",
                        "voice" to "af_heart",
                        "sample_rate" to 24000,
                        "port" to 8766
                    )))

                session.method == Method.POST && session.uri == "/v1/audio/speech" -> handleSpeech(session)
                else -> json(Response.Status.NOT_FOUND, JSONObject(mapOf("error" to "not_found")))
            }
        } catch (t: Throwable) {
            json(
                Response.Status.INTERNAL_ERROR,
                JSONObject(mapOf("error" to (t.message ?: t.javaClass.simpleName)))
            )
        }
    }

    private fun handleSpeech(session: IHTTPSession): Response {
        val files = HashMap<String, String>()
        session.parseBody(files)
        val raw = files["postData"].orEmpty()
        if (raw.isBlank()) return json(Response.Status.BAD_REQUEST, JSONObject(mapOf("error" to "empty_body")))

        val body = JSONObject(raw)
        val text = when {
            body.has("input") -> body.optString("input")
            body.has("text") -> body.optString("text")
            else -> ""
        }.trim()
        if (text.isEmpty()) return json(Response.Status.BAD_REQUEST, JSONObject(mapOf("error" to "input_required")))
        if (text.length > 10_000) return json(Response.Status.BAD_REQUEST, JSONObject(mapOf("error" to "input_too_long")))

        val voice = body.optString("voice", "af_heart")
        if (voice != "af_heart") return json(Response.Status.BAD_REQUEST, JSONObject(mapOf("error" to "voice_not_available", "available" to "af_heart")))

        val responseFormat = body.optString("response_format", "wav")
        if (responseFormat != "wav") return json(Response.Status.BAD_REQUEST, JSONObject(mapOf("error" to "only_wav_is_supported")))

        val speed = body.optDouble("speed", 1.0).toFloat()
        if (speed !in 0.5f..2.0f) return json(Response.Status.BAD_REQUEST, JSONObject(mapOf("error" to "speed_out_of_range", "min" to 0.5, "max" to 2.0)))

        val wav = runBlocking(Dispatchers.Default) { engine.synthesize(text, speed) }
        val response = newFixedLengthResponse(
            Response.Status.OK,
            "audio/wav",
            ByteArrayInputStream(wav),
            wav.size.toLong()
        )
        response.addHeader("Cache-Control", "no-store")
        return cors(response)
    }

    private fun json(status: Response.Status, obj: JSONObject): Response =
        cors(newFixedLengthResponse(status, "application/json; charset=utf-8", obj.toString()))

    private fun cors(response: Response): Response {
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS")
        response.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization")
        response.addHeader("Access-Control-Allow-Private-Network", "true")
        return response
    }
}
