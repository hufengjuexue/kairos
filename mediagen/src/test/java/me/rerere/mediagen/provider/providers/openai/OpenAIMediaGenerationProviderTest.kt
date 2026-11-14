package me.rerere.mediagen.provider.providers.openai

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationManager
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.providers.MediaGenerationApiException
import me.rerere.mediagen.provider.providers.bodyAsString
import me.rerere.mediagen.provider.providers.fakeClient
import me.rerere.mediagen.provider.providers.jsonResponse
import me.rerere.mediagen.provider.providers.mediaGenerationJson
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class OpenAIMediaGenerationProviderTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private val setting = MediaGenerationProviderSetting.OpenAI(
        apiKey = "sk-test",
        baseUrl = "https://example.com/v1/",
    )
    private val model = MediaGenerationModel(modelId = "gpt-image-2", kind = MediaKind.IMAGE)

    @Test
    fun generationBodyLetsPublicFieldsOverrideExtraParameters() {
        val body = OpenAIMediaGenerationProvider(OkHttpClient()).buildGenerationBody(
            setting = setting,
            model = model,
            request = MediaGenerationRequest(
                prompt = "一只猫",
                count = 2,
                resolution = "2048x1152",
                extraParameters = buildJsonObject {
                    put("n", 9)
                    put("quality", "high")
                },
            ),
        )

        assertEquals(JsonPrimitive("gpt-image-2"), body["model"])
        assertEquals(JsonPrimitive("一只猫"), body["prompt"])
        assertEquals(JsonPrimitive(2), body["n"])
        assertEquals(JsonPrimitive("2048x1152"), body["size"])
        assertEquals(JsonPrimitive("high"), body["quality"])
    }

    @Test
    fun generationBodyOmitsAutoSizeAndGrokSize() {
        val provider = OpenAIMediaGenerationProvider(OkHttpClient())

        val auto = provider.buildGenerationBody(setting, model, MediaGenerationRequest(prompt = "cat", resolution = "auto"))
        assertNull(auto["size"])
        assertNull(auto["n"])

        val grok = provider.buildGenerationBody(
            setting = setting.copy(baseUrl = "https://api.x.ai/v1"),
            model = model.copy(modelId = "grok-imagine-image"),
            request = MediaGenerationRequest(prompt = "cat", resolution = "1024x1024"),
        )
        assertNull(grok["size"])

        // 域名只是以 x.ai 结尾的中转不应被当成 Grok
        val relay = provider.buildGenerationBody(
            setting = setting.copy(baseUrl = "https://api.foo-max.ai/v1"),
            model = model,
            request = MediaGenerationRequest(prompt = "cat", resolution = "1024x1024"),
        )
        assertEquals(JsonPrimitive("1024x1024"), relay["size"])
    }

    @Test
    fun rejectsFieldsTheImageApiDoesNotExpose() {
        val provider = OpenAIMediaGenerationProvider(OkHttpClient())

        assertThrows(IllegalArgumentException::class.java) {
            provider.buildGenerationBody(setting, model, MediaGenerationRequest(prompt = "cat", durationSeconds = 5))
        }
        assertThrows(IllegalArgumentException::class.java) {
            provider.buildEditBody(
                setting = setting,
                model = model,
                request = MediaGenerationRequest(inputs = listOf(MediaGenerationInput.Image("/tmp/a.png"))),
            )
        }
    }

    @Test
    fun generateReturnsTerminalTaskWithDecodedImages() = runBlocking {
        val requests = mutableListOf<Request>()
        val manager = MediaGenerationManager(fakeClient(requests) {
            jsonResponse(
                request = it,
                body = """
                    {
                      "created": 1785125529,
                      "output_format": "webp",
                      "size": "1024x1024",
                      "data": [{"b64_json": "aGVsbG8="}],
                      "usage": {"total_tokens": 4160}
                    }
                """.trimIndent(),
            )
        })

        val tasks = manager.generate(setting, model, MediaGenerationRequest(prompt = "cat")).toList()

        val request = requests.single()
        assertEquals("https://example.com/v1/images/generations", request.url.toString())
        assertEquals("Bearer sk-test", request.header("Authorization"))
        val sent = mediaGenerationJson.parseToJsonElement(request.bodyAsString()).jsonObject
        assertEquals(JsonPrimitive("cat"), sent["prompt"])

        val task = tasks.single()
        assertEquals(MediaGenerationStatus.SUCCEEDED, task.status)
        assertEquals("gpt-image-2", task.model)
        assertEquals(1785125529L, task.createdAtEpochSeconds)
        assertEquals(4160L, task.usage?.totalTokens)
        assertFalse("data" in task.metadata)
        val output = task.outputs.single()
        assertEquals("hello", output.data!!.decodeToString())
        assertNull(output.url)
        assertEquals("image/webp", output.mimeType)
        assertEquals("1024x1024", output.resolution)
    }

    @Test
    fun urlOnlyImagesAreReturnedAsUrlOutputs() {
        val root = mediaGenerationJson.parseToJsonElement(
            """{"data":[{"url":"https://cdn.example.com/out.png"}]}"""
        ).jsonObject

        val output = OpenAIMediaGenerationProvider(OkHttpClient()).parseTask(root).outputs.single()
        assertEquals("https://cdn.example.com/out.png", output.url)
        assertNull(output.data)
        assertEquals("image/png", output.mimeType)
    }

    @Test
    fun blankBase64FallsBackToUrl() {
        val root = mediaGenerationJson.parseToJsonElement(
            """{"data":[{"url":"https://cdn.example.com/out.png","b64_json":"","revised_prompt":null}]}"""
        ).jsonObject

        val output = OpenAIMediaGenerationProvider(OkHttpClient()).parseTask(root).outputs.single()
        assertEquals("https://cdn.example.com/out.png", output.url)
        assertNull(output.data)
    }

    @Test
    fun imageInputsAreUploadedToEditEndpointAsMultipart() = runBlocking {
        val first = tempFolder.newFile("a.png").apply { writeText("png-bytes") }
        val second = tempFolder.newFile("b.JPG").apply { writeText("jpg-bytes") }
        val requests = mutableListOf<Request>()
        val bodies = mutableListOf<String>()
        val manager = MediaGenerationManager(fakeClient(requests) {
            bodies += it.bodyAsString()
            jsonResponse(it, """{"data":[{"b64_json":"aGVsbG8="}]}""")
        })

        val task = manager.create(
            setting = setting,
            model = model,
            request = MediaGenerationRequest(
                prompt = "换成夜景",
                inputs = listOf(
                    MediaGenerationInput.Image(first.absolutePath),
                    MediaGenerationInput.Image("file://${second.absolutePath}"),
                ),
                resolution = "1024x1536",
                extraParameters = buildJsonObject { put("input_fidelity", "high") },
            ),
        ).getOrThrow()

        val request = requests.single()
        val body = bodies.single()
        assertEquals("https://example.com/v1/images/edits", request.url.toString())
        assertTrue(request.body!!.contentType().toString().startsWith("multipart/form-data"))
        assertTrue(body.contains("name=\"image[]\"; filename=\"a.png\""))
        assertTrue(body.contains("name=\"image[]\"; filename=\"b.JPG\""))
        assertTrue(body.contains("Content-Type: image/jpeg"))
        assertTrue(body.contains("png-bytes"))
        assertTrue(body.contains("name=\"prompt\""))
        assertTrue(body.contains("换成夜景"))
        assertTrue(body.contains("name=\"size\""))
        assertTrue(body.contains("name=\"input_fidelity\""))
        assertEquals("image/png", task.outputs.single().mimeType)
    }

    @Test
    fun editUsesSingleImageFieldAndRejectsUnsupportedInputs() {
        val provider = OpenAIMediaGenerationProvider(OkHttpClient())
        val png = tempFolder.newFile("only.png")
        val gif = tempFolder.newFile("anim.gif")
        fun editBody(vararg inputs: MediaGenerationInput) =
            provider.buildEditBody(setting, model, MediaGenerationRequest(prompt = "edit", inputs = inputs.toList()))

        val disposition = editBody(MediaGenerationInput.Image(png.absolutePath))
            .parts.last().headers!!["Content-Disposition"]!!
        assertTrue(disposition.contains("name=\"image\""))
        assertFalse(disposition.contains("image[]"))

        assertThrows(IllegalArgumentException::class.java) { editBody(MediaGenerationInput.Image(gif.absolutePath)) }
        assertThrows(IllegalArgumentException::class.java) { editBody(MediaGenerationInput.Image("/not/exist.png")) }
        assertThrows(IllegalArgumentException::class.java) {
            editBody(MediaGenerationInput.Video("https://example.com/a.mp4"))
        }
    }

    @Test
    fun apiErrorIsSurfacedWithStatusAndCode() = runBlocking {
        val manager = MediaGenerationManager(fakeClient {
            jsonResponse(
                request = it,
                body = """{"error":{"message":"Billing hard limit has been reached","code":"billing_hard_limit_reached"}}""",
                code = 400,
            )
        })

        val error = manager.create(setting, model, MediaGenerationRequest(prompt = "cat")).exceptionOrNull()
            as MediaGenerationApiException
        assertEquals(400, error.statusCode)
        assertEquals("billing_hard_limit_reached", error.code)
        assertEquals("Billing hard limit has been reached", error.message)
    }

    @Test
    fun synchronousProviderHasNoTaskToQuery() = runBlocking {
        val manager = MediaGenerationManager(fakeClient { error("query must not hit the network") })

        assertTrue(manager.query(setting, model, "any").exceptionOrNull() is UnsupportedOperationException)
    }

    @Test
    fun videoCreateAndPollWorkAndRejectsUnsupportedKindOnMiniMax() = runBlocking {
        val requests = mutableListOf<Request>()
        var pollCount = 0
        val client = fakeClient(requests) { request ->
            val path = request.url.encodedPath
            when {
                request.method == "POST" && path == "/v1/videos" -> jsonResponse(
                    request,
                    """{"id":"video_1","status":"queued","model":"sora-2"}""",
                )

                request.method == "GET" && path == "/v1/videos/video_1" -> {
                    pollCount++
                    jsonResponse(
                        request,
                        """{"id":"video_1","status":"completed","model":"sora-2","size":"1280x720"}""",
                    )
                }

                request.method == "GET" && path == "/v1/videos/video_2" -> jsonResponse(
                    request,
                    """{"id":"video_2","status":"completed","video_url":"https://cdn.example.com/out.mp4"}""",
                )

                else -> error("unexpected request: ${request.method} $path")
            }
        }
        val manager = MediaGenerationManager(client)
        val video = MediaGenerationModel(modelId = "sora-2", kind = MediaKind.VIDEO)

        assertEquals(setOf(MediaKind.IMAGE, MediaKind.VIDEO), setting.supportedKinds)

        val created = manager.create(
            setting,
            video,
            MediaGenerationRequest(prompt = "一只飞翔的猫", aspectRatio = "16:9", durationSeconds = 8),
        ).getOrThrow()
        assertEquals("video_1", created.id)
        assertEquals(MediaGenerationStatus.QUEUED, created.status)
        assertTrue(requests.single().bodyAsString().contains("\"seconds\":\"8\""))

        val queried = manager.query(setting, video, "video_1").getOrThrow()
        assertEquals(1, pollCount)
        assertEquals(MediaGenerationStatus.SUCCEEDED, queried.status)
        assertEquals(
            "https://example.com/v1/videos/video_1/content",
            queried.outputs.single().url,
        )
        assertEquals(
            mapOf("Authorization" to "Bearer sk-test"),
            setting.downloadHeaders("https://example.com/v1/videos/video_1/content"),
        )
        assertTrue(setting.downloadHeaders("https://other-cdn.example.com/video.mp4").isEmpty())

        val relayVideo = manager.query(setting, video, "video_2").getOrThrow()
        assertEquals("https://cdn.example.com/out.mp4", relayVideo.outputs.single().url)

        val miniMax = MediaGenerationProviderSetting.MiniMax(apiKey = "mm-test")
        val unsupported = manager.create(
            miniMax,
            MediaGenerationModel(modelId = "image-01", kind = MediaKind.IMAGE),
            MediaGenerationRequest(prompt = "cat"),
        ).exceptionOrNull()
        assertTrue(unsupported is UnsupportedOperationException)
        assertEquals("minimax does not support IMAGE generation", unsupported!!.message)
    }
}
