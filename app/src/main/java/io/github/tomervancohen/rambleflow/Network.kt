package io.github.tomervancohen.rambleflow

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

class RemoteFailure(val kind: String, val status: Int = 0, val retryAfter: Long = 15 * 60_000) :
    IOException(kind)

interface Transcriber {
    fun transcribe(provider: String, key: String, file: File): String
}

class ProviderApi internal constructor(private val client: OkHttpClient = safeClient(),
    private val language: () -> String = { "" }) :
    Transcriber {
    companion object {
        private fun safeClient() =
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .callTimeout(120, TimeUnit.SECONDS)
                .readTimeout(100, TimeUnit.SECONDS)
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .followSslRedirects(false)
                .build()
    }

    private fun base(provider: String) =
        when (provider.substringBefore("@")) {
            "groq" -> "https://api.groq.com/openai/v1"
            "openai" -> "https://api.openai.com/v1"
            else -> error("provider")
        }

    fun validate(provider: String, key: String) {
        client
            .newCall(
                Request.Builder()
                    .url(base(provider) + "/models")
                    .header("Authorization", "Bearer $key")
                    .build()
            )
            .execute()
            .use { response ->
                if (!response.isSuccessful)
                    throw RemoteFailure(
                        Policy.disposition(response.code),
                        response.code,
                        (response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 15 * 60_000)
                            .coerceIn(30_000, 6 * 60 * 60_000),
                    )
            }
    }

    override fun transcribe(provider: String, key: String, file: File): String {
        if(file.length() !in 1 until Policy.MAX_UPLOAD) throw RemoteFailure("rejected",if(file.length()>=Policy.MAX_UPLOAD)413 else 400)
        if (WavSignal.isDigitalSilence(file)) throw RemoteFailure("no_speech")
        val model =
            if (provider.substringBefore("@") == "groq") "whisper-large-v3" else "gpt-transcribe"
        val builder =
            MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("model", model)
                .addFormDataPart("file", "chunk.wav", file.asRequestBody("audio/wav".toMediaType()))
        language().takeIf { it.isNotBlank() }?.let {
            builder.addFormDataPart(if (provider.substringBefore("@") == "openai") "languages[]" else "language", it)
        }
        if (provider.substringBefore("@") == "groq")
            builder.addFormDataPart("response_format", "verbose_json")
        val body = builder.build()
        val request =
            Request.Builder()
                .url(base(provider) + "/audio/transcriptions")
                .header("Authorization", "Bearer $key")
                .post(body)
                .build()
        var uploadStarted = false
        val tracked = client.newBuilder().retryOnConnectionFailure(false)
            .followRedirects(false).followSslRedirects(false)
            .eventListener(object : EventListener() {
                override fun requestBodyStart(call: Call) { uploadStarted = true }
            }).build()
        try {
        return tracked.newCall(request).execute().use { response ->
            if (!response.isSuccessful)
                throw RemoteFailure(
                    Policy.disposition(response.code),
                    response.code,
                    (response.header("Retry-After")?.toLongOrNull()?.times(1000) ?: 15 * 60_000)
                        .coerceIn(30_000, 6 * 60 * 60_000),
                )
            val source = response.body?.source() ?: throw RemoteFailure("uncertain")
            if (source.request(2 * 1024 * 1024 + 1L)) throw RemoteFailure("uncertain")
            val json = runCatching {
                JSONObject(source.readUtf8())
            }.getOrElse { throw RemoteFailure("uncertain") }
            if (!json.has("text") || json.isNull("text")) throw RemoteFailure("uncertain")
            val segments = json.optJSONArray("segments")
            if (segments != null && segments.length() > 0 &&
                (0 until segments.length()).all { i ->
                    val segment = segments.getJSONObject(i)
                    segment.optDouble("no_speech_prob", 0.0) > 0.6 &&
                        segment.optDouble("avg_logprob", 0.0) < -1.0
                }) throw RemoteFailure("no_speech")
            json.getString("text").trim().also {
                if (it.isBlank()) throw RemoteFailure("no_speech")
                if (it.toByteArray().size > 256 * 1024) throw RemoteFailure("uncertain")
            }
        }
        } catch (error: IOException) {
            if (error is RemoteFailure) throw error
            // No body could have reached the provider before DNS/connect/TLS completed.
            throw RemoteFailure(if (uploadStarted) "uncertain" else "network", retryAfter = 60_000)
        }

    }
}
