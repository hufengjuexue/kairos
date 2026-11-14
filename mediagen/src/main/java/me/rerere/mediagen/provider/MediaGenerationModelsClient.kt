package me.rerere.mediagen.provider

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.model.inferMediaKind
import me.rerere.mediagen.model.looksLikeMediaGenerationModel
import me.rerere.mediagen.provider.providers.bearerRequest
import me.rerere.mediagen.provider.providers.executeJson
import me.rerere.mediagen.provider.providers.string
import okhttp3.OkHttpClient

/** 从媒体供应商接口拉取到的远端模型条目。 */
data class RemoteMediaModel(
    val modelId: String,
    val kind: MediaKind,
    val displayName: String = "",
    val isRecommendedMedia: Boolean = true,
)

/**
 * 拉取媒体供应商的远端模型列表并识别生图 / 视频类型。
 */
internal class MediaGenerationModelsClient(
    private val client: OkHttpClient,
) {
    suspend fun listModels(setting: MediaGenerationProviderSetting): List<RemoteMediaModel> = when (setting) {
        is MediaGenerationProviderSetting.OpenRouter -> listOpenRouterModels(setting)
        is MediaGenerationProviderSetting.Aliyun -> listStandardModels(
            apiKey = setting.apiKey,
            baseUrl = resolveAliyunBaseUrl(setting),
            supportedKinds = setting.supportedKinds,
            providerId = "aliyun",
        )

        is MediaGenerationProviderSetting.OpenAI,
        is MediaGenerationProviderSetting.Volcengine,
        is MediaGenerationProviderSetting.MiniMax -> listStandardModels(
            apiKey = setting.apiKey,
            baseUrl = setting.baseUrl,
            supportedKinds = setting.supportedKinds,
            providerId = setting.name.ifBlank { "media" },
        )
    }

    private suspend fun listOpenRouterModels(
        setting: MediaGenerationProviderSetting.OpenRouter,
    ): List<RemoteMediaModel> {
        val baseUrl = setting.baseUrl.trimEnd('/')
        val results = linkedMapOf<String, RemoteMediaModel>()

        val videoResult = runCatching {
            val req = bearerRequest("$baseUrl/videos/models", setting.apiKey)
                .addHeader("X-Title", "Kairos")
                .addHeader("HTTP-Referer", "https://github.com/hufengjuexue/kairos")
                .get()
                .build()
            parseModelsArray(client.executeJson(req, "openrouter"), MediaKind.VIDEO)
        }
        val imageResult = runCatching {
            val req = bearerRequest("$baseUrl/images/models", setting.apiKey)
                .addHeader("X-Title", "Kairos")
                .addHeader("HTTP-Referer", "https://github.com/hufengjuexue/kairos")
                .get()
                .build()
            parseModelsArray(client.executeJson(req, "openrouter"), MediaKind.IMAGE)
        }

        imageResult.getOrNull()?.forEach { results[it.modelId] = it }
        videoResult.getOrNull()?.forEach { results[it.modelId] = it }
        if (results.isNotEmpty()) return results.values.sortedBy { it.modelId }

        return listStandardModels(
            apiKey = setting.apiKey,
            baseUrl = baseUrl,
            supportedKinds = setting.supportedKinds,
            providerId = "openrouter",
        )
    }

    private suspend fun listStandardModels(
        apiKey: String,
        baseUrl: String,
        supportedKinds: Set<MediaKind>,
        providerId: String,
    ): List<RemoteMediaModel> {
        val url = "${baseUrl.trimEnd('/')}/models"
        val root = client.executeJson(bearerRequest(url, apiKey).get().build(), providerId)
        val array = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val rawId = obj.string("id") ?: obj.string("name") ?: return@mapNotNull null
            val modelId = rawId.substringAfter("models/").trim()
            if (modelId.isEmpty()) return@mapNotNull null
            val displayName = obj.string("name")?.takeIf { it != modelId }
                ?: obj.string("display_name")
                ?: ""
            val inferred = inferMediaKind(modelId)
            val kind = if (inferred in supportedKinds) inferred else supportedKinds.firstOrNull() ?: inferred
            RemoteMediaModel(
                modelId = modelId,
                kind = kind,
                displayName = displayName,
                isRecommendedMedia = looksLikeMediaGenerationModel(modelId),
            )
        }.distinctBy { it.modelId }.sortedBy { it.modelId }
    }

    private fun parseModelsArray(root: JsonObject, fixedKind: MediaKind): List<RemoteMediaModel> {
        val array = (root["data"] as? JsonArray) ?: (root["models"] as? JsonArray) ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element.jsonObject
            val modelId = (obj.string("id") ?: obj.string("slug"))?.trim() ?: return@mapNotNull null
            if (modelId.isEmpty()) return@mapNotNull null
            RemoteMediaModel(
                modelId = modelId,
                kind = fixedKind,
                displayName = obj.string("name")?.takeIf { it != modelId }.orEmpty(),
                isRecommendedMedia = true,
            )
        }
    }

    private fun resolveAliyunBaseUrl(setting: MediaGenerationProviderSetting.Aliyun): String {
        val raw = setting.baseUrl.trimEnd('/')
        return if (MediaGenerationProviderSetting.Aliyun.WORKSPACE_PLACEHOLDER in raw && setting.workspaceId.isNotBlank()) {
            raw.replace(MediaGenerationProviderSetting.Aliyun.WORKSPACE_PLACEHOLDER, setting.workspaceId.trim())
        } else {
            raw
        }
    }
}
