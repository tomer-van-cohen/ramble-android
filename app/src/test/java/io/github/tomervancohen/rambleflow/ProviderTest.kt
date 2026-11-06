package io.github.tomervancohen.rambleflow

import java.io.File
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.*
import org.junit.Assert.*

class ProviderTest {
    private lateinit var server: MockWebServer
    private lateinit var api: ProviderApi
    private lateinit var file: File

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
        val client =
            OkHttpClient.Builder()
                .retryOnConnectionFailure(false)
                .followRedirects(false)
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .url(server.url(chain.request().url.encodedPath))
                            .build()
                    )
                }
                .build()
        api = ProviderApi(client)
        file = File.createTempFile("synthetic-", ".wav").apply { writeBytes(ByteArray(44)) }
    }

    @After
    fun close() {
        server.shutdown()
        file.delete()
    }

    @Test
    fun sendsOneMultipartRequestWithBoundProfileAndNormalizesText() {
        server.enqueue(MockResponse().setBody("{\"text\":\" שלום world \"}"))
        assertEquals("שלום world", api.transcribe("groq@retained-profile", "synthetic-key", file))
        val r = server.takeRequest()
        assertEquals("/openai/v1/audio/transcriptions", r.path)
        assertEquals("Bearer synthetic-key", r.getHeader("Authorization"))
        val body = r.body.readUtf8()
        assertTrue(body.contains("whisper-large-v3"))
        assertTrue(body.contains("chunk.wav"))
        assertEquals(1, server.requestCount)
    }

    @Test
    fun openAiUsesCurrentModel() {
        server.enqueue(MockResponse().setBody("{\"text\":\"test\"}"))
        api.transcribe("openai@old-profile", "synthetic", file)
        assertTrue(server.takeRequest().body.readUtf8().contains("gpt-transcribe"))
    }

    @Test
    fun modelCheckSendsNoAudio() {
        server.enqueue(MockResponse().setBody("{}"))
        api.validate("groq", "synthetic")
        val r = server.takeRequest()
        assertEquals("GET", r.method)
        assertEquals(0, r.bodySize)
        assertEquals("/openai/v1/models", r.path)
    }

    @Test
    fun lostOrErrorResponseDoesNotRetry() {
        server.enqueue(MockResponse().setResponseCode(503))
        try {
            api.transcribe("groq", "synthetic", file)
            fail()
        } catch (e: RemoteFailure) {
            assertEquals("uncertain", e.kind)
        }
        assertEquals(1, server.requestCount)
    }

    @Test
    fun rateRejectionRetainsRetryAfter() {
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))
        try {
            api.transcribe("groq", "synthetic", file)
            fail()
        } catch (e: RemoteFailure) {
            assertEquals("rate", e.kind)
            assertEquals(120000L, e.retryAfter)
        }
    }

    @Test
    fun malformedSuccessIsUncertain() {
        server.enqueue(MockResponse().setBody("not json"))
        try {
            api.transcribe("groq", "synthetic", file)
            fail()
        } catch (e: RemoteFailure) {
            assertEquals("uncertain", e.kind)
        }
    }

    @Test(expected = RemoteFailure::class)
    fun silenceIsNotSuccessfulTranscription() {
        server.enqueue(MockResponse().setBody("{\"text\":\"\"}"))
        api.transcribe("groq", "synthetic", file)
    }

    @Test
    fun selectedLanguageIsSentAndOneWordIsAccepted() {
        api = ProviderApi(OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url("/audio/transcriptions")).build())
        }.build(), language = { "he" })
        server.enqueue(MockResponse().setBody("""{"text":"שלום","segments":[{"no_speech_prob":0.01,"avg_logprob":-0.2}]}"""))
        assertEquals("שלום", api.transcribe("groq", "synthetic", file))
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body.contains("name=\"language\"") && body.contains("\r\nhe\r\n"))
        assertTrue(body.contains("verbose_json"))
    }

    @Test
    fun hallucinatedTextOverNonSpeechIsRejected() {
        server.enqueue(MockResponse().setBody("""{"text":"Made up words","segments":[{"no_speech_prob":0.95,"avg_logprob":-1.8}]}"""))
        try { api.transcribe("groq", "synthetic", file); fail() }
        catch (e: RemoteFailure) { assertEquals("no_speech", e.kind) }
    }

    @Test
    fun lowConfidenceAloneDoesNotDiscardShortSpeech() {
        server.enqueue(MockResponse().setBody("""{"text":"Hi","segments":[{"no_speech_prob":0.02,"avg_logprob":-1.2}]}"""))
        assertEquals("Hi", api.transcribe("groq", "synthetic", file))
    }


    @Test
    fun rejectsUnknownProviderBeforeNetwork() {
        try {
            api.validate("evil", "synthetic")
            fail()
        } catch (_: IllegalStateException) {}
        assertEquals(0, server.requestCount)
    }
    @Test fun openAiLanguageUsesArrayField() {
        api = ProviderApi(clientForTest(), language = { "he" })
        server.enqueue(MockResponse().setBody("{\"text\":\"שלום\"}"))
        assertEquals("שלום", api.transcribe("openai","synthetic",file))
        val body=server.takeRequest().body.readUtf8()
        assertTrue(body.contains("name=\"languages[]\""))
        assertFalse(body.contains("name=\"language\""))
    }
    private fun clientForTest() = OkHttpClient.Builder().addInterceptor { chain ->
        chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
    }.build()
    @Test fun definitiveRejectionsAreNotPossibleCharges() {
        for (code in listOf(400,404,413,415,422)) {
            server.enqueue(MockResponse().setResponseCode(code))
            try { api.transcribe("groq","synthetic",file);fail() }
            catch (e: RemoteFailure) { assertEquals("rejected",e.kind);assertEquals(code,e.status) }
        }
        assertEquals(5,server.requestCount)
    }
    @Test fun dnsFailureBeforeUploadIsSafelyRetryable() {
        api=ProviderApi(OkHttpClient.Builder().dns(object : okhttp3.Dns { override fun lookup(hostname: String): List<java.net.InetAddress> { throw java.net.UnknownHostException("synthetic-dns") } }).build())
        try { api.transcribe("groq","synthetic",file);fail() }
        catch(e: RemoteFailure) { assertEquals("network",e.kind) }
        assertEquals(0,server.requestCount)
    }
    @Test fun disconnectAfterUploadRemainsUncertain() {
        server.enqueue(MockResponse().setSocketPolicy(okhttp3.mockwebserver.SocketPolicy.DISCONNECT_AFTER_REQUEST))
        try { api.transcribe("groq","synthetic",file);fail() }
        catch(e: RemoteFailure) { assertEquals("uncertain",e.kind) }
        assertEquals(1,server.requestCount)
    }

}
