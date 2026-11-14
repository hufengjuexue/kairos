package me.rerere.mediagen.provider.providers.openai

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import me.rerere.mediagen.model.MediaGenerationError
import me.rerere.mediagen.model.MediaGenerationInput
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaGenerationOutput
import me.rerere.mediagen.model.MediaGenerationRequest
import me.rerere.mediagen.model.MediaGenerationStatus
import me.rerere.mediagen.model.MediaGenerationTask
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.providers.executeJson
import me.rerere.mediagen.provider.providers.long
import me.rerere.mediagen.provider.providers.obj
import me.rerere.mediagen.provider.providers.postJson
import me.rerere.mediagen.provider.providers.string
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * OpenAI 及兼容中转站的视频接口（`/videos` / Sora 2）：提交后轮询任务状态，
 * 完成时优先读取中转直链，否则从 `{baseUrl}/videos/{id}/content` 带鉴权头下载。
 */
internal class OpenAIVideoGeneration(
    private val client: OkHttpClient,
) {
    private val id = OpenAIMediaGenerationProvider.PROVIDER_ID

    suspend fun create(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MediaGenerationTask {
        val baseUrl = setting.baseUrl.trimEnd('/')
        val hasLocalFile = request.inputs.any { input ->
            input is MediaGenerationInput.Image && isLocalFilePath(input.url)
        }
        val httpRequest = OpenAIMediaGenerationProvider.authorizedRequest(setting, "$baseUrl/videos").let { builder ->
            if (hasLocalFile) {
                builder.post(buildCreateMultipart(model, request))
            } else {
                builder.postJson(buildCreateBody(model, request))
            }
        }.build()
        return parseTask(setting, client.executeJson(httpRequest, id), model.modelId)
    }

    suspend fun query(
        setting: MediaGenerationProviderSetting.OpenAI,
        model: MediaGenerationModel,
        taskId: String,
    ): MediaGenerationTask {
        val baseUrl = setting.baseUrl.trimEnd('/')
        val httpRequest = OpenAIMediaGenerationProvider.authorizedRequest(setting, "$baseUrl/videos/$taskId")
            .get()
            .build()
        return parseTask(setting, client.executeJson(httpRequest, id), model.modelId)
    }

    internal fun buildCreateBody(
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): JsonObject = buildJsonObject {
        validate(request)
        request.extraParameters.forEach { (key, value) -> put(key, value) }
        put("model", model.modelId)
        put("prompt", request.prompt)
        resolveSeconds(request)?.let { put("seconds", it) }
        resolveVideoSize(request)?.let { put("size", it) }
        val firstImage = request.inputs.filterIsInstance<MediaGenerationInput.Image>().firstOrNull()
        if (firstImage != null) {
            put("input_reference", buildJsonObject { put("image_url", firstImage.url) })
        }
    }

    internal fun buildCreateMultipart(
        model: MediaGenerationModel,
        request: MediaGenerationRequest,
    ): MultipartBody {
        validate(request)
        val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
        request.extraParameters.forEach { (key, value) ->
            builder.addFormDataPart(key, (value as? JsonPrimitive)?.contentOrNull ?: value.toString())
        }
        builder.addFormDataPart("model", model.modelId)
        builder.addFormDataPart("prompt", request.prompt.orEmpty())
        resolveSeconds(request)?.let { builder.addFormDataPart("seconds", it) }
        resolveVideoSize(request)?.let { builder.addFormDataPart("size", it) }

        val firstImage = request.inputs.filterIsInstance<MediaGenerationInput.Image>().firstOrNull()
        if (firstImage != null) {
            val file = File(firstImage.url.removePrefix("file://"))
            require(file.exists()) { "Image file does not exist: ${firstImage.url}" }
            val mediaType = OpenAIMediaGenerationProvider.EDIT_IMAGE_MEDIA_TYPES[file.extension.lowercase()]
                ?: throw IllegalArgumentException("Unsupported image file type for OpenAI video reference: ${file.extension}")
            builder.addFormDataPart("input_reference", file.name, file.asRequestBody(mediaType.toMediaType()))
        }
        return builder.build()
    }

    internal fun parseTask(
        setting: MediaGenerationProviderSetting.OpenAI,
        root: JsonObject,
        fallbackModel: String? = null,
    ): MediaGenerationTask {
        val taskId = root.string("id") ?: error("OpenAI video response does not contain id")
        val status = mapStatus(root.string("status"))
        val directUrls = extractDirectUrls(root)
        val outputUrls = when {
            directUrls.isNotEmpty() -> directUrls
            status == MediaGenerationStatus.SUCCEEDED -> listOf("${setting.baseUrl.trimEnd('/')}/videos/$taskId/content")
            else -> emptyList()
        }
        val errorMessage = when (val failure = root["error"]) {
            is JsonObject -> failure.string("message")
            is JsonPrimitive -> failure.contentOrNull
            else -> null
        }
        return MediaGenerationTask(
            id = taskId,
            provider = id,
            model = root.string("model") ?: fallbackModel,
            status = status,
            outputs = outputUrls.map { url ->
                MediaGenerationOutput(
                    url = url,
                    mimeType = "video/mp4",
                    resolution = root.string("size"),
                )
            },
            error = errorMessage?.let { MediaGenerationError(message = it) },
            createdAtEpochSeconds = root.long("created_at"),
            metadata = root,
        )
    }

    private fun validate(request: MediaGenerationRequest) {
        require(!request.prompt.isNullOrBlank()) { "OpenAI video generation requires a non-empty prompt" }
        request.inputs.forEach { input ->
            require(input is MediaGenerationInput.Image) {
                "OpenAI video generation only supports image reference inputs, got ${input::class.simpleName}"
            }
        }
        require(request.count == null || request.count == 1) { "OpenAI generates one video per job" }
        require(request.generateAudio == null) { "OpenAI video generation does not expose generateAudio" }
        require(request.watermark == null) { "OpenAI video generation does not expose watermark" }
        require(request.seed == null) { "OpenAI video generation does not expose seed" }
        require(request.promptEnhancement == null) { "OpenAI video generation does not expose prompt enhancement" }
        require(request.callbackUrl == null) { "OpenAI video generation does not expose callbackUrl" }
    }

    private fun resolveSeconds(request: MediaGenerationRequest): String? =
        request.durationSeconds?.takeIf { it > 0 }?.toString()

    private fun resolveVideoSize(request: MediaGenerationRequest): String? {
        val raw = request.resolution?.trim()?.takeIf { it.isNotEmpty() && !it.equals("auto", ignoreCase = true) }
        val portrait = request.aspectRatio == "9:16" || request.aspectRatio == "3:4"
        if (raw != null) {
            val normalized = raw.replace('*', 'x')
            if (PIXEL_SIZE_REGEX.matches(normalized)) return normalized
            return when (normalized.uppercase()) {
                "480P", "720P" -> if (portrait) "720x1280" else "1280x720"
                "1080P", "2K", "4K" -> if (portrait) "1024x1792" else "1792x1024"
                else -> normalized
            }
        }
        return when (request.aspectRatio) {
            "9:16", "3:4" -> "720x1280"
            "16:9", "4:3", "21:9" -> "1280x720"
            "1:1" -> "1024x1024"
            else -> null
        }
    }

    private fun extractDirectUrls(root: JsonObject): List<String> {
        val urls = linkedSetOf<String>()
        listOf("video_url", "url").forEach { key ->
            root.string(key)?.takeIf { it.isNotBlank() }?.let { urls += it }
        }
        root.obj("output")?.string("url")?.takeIf { it.isNotBlank() }?.let { urls += it }
        (root["data"] as? JsonArray)?.forEach { element ->
            (element as? JsonObject)?.let { obj ->
                (obj.string("url") ?: obj.string("video_url"))?.takeIf { it.isNotBlank() }?.let { urls += it }
            }
        }
        return urls.toList()
    }

    private fun isLocalFilePath(url: String): Boolean {
        val trimmed = url.trim()
        return !trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true) &&
            !trimmed.startsWith("data:", ignoreCase = true)
    }

    private fun mapStatus(status: String?): MediaGenerationStatus = when (status?.lowercase()) {
        "queued", "pending" -> MediaGenerationStatus.QUEUED
        "in_progress", "running", "processing", "submitted" -> MediaGenerationStatus.RUNNING
        "completed", "succeeded" -> MediaGenerationStatus.SUCCEEDED
        "failed", "error" -> MediaGenerationStatus.FAILED
        "cancelled", "canceled" -> MediaGenerationStatus.CANCELLED
        "expired" -> MediaGenerationStatus.EXPIRED
        else -> MediaGenerationStatus.UNKNOWN
    }

    private companion object {
        val PIXEL_SIZE_REGEX = Regex("""^\d+[xX]\d+$""")
    }
}
