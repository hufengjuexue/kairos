package me.rerere.mediagen.model

import kotlinx.coroutines.runBlocking
import me.rerere.mediagen.provider.MediaGenerationManager
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.providers.fakeClient
import me.rerere.mediagen.provider.providers.jsonResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaGenerationModelInferenceTest {
    @Test
    fun inferMediaKindIdentifiesVideoAndImageModels() {
        val videoIds = listOf(
            "sora-2",
            "veo-3.0-generate-001",
            "doubao-seedance-1-0-pro-250528",
            "kling-v2-master",
            "MiniMax-Hailuo-2.3",
            "wan2.6-t2v",
            "wan2.1-turbo",
            "hunyuan-video",
        )
        videoIds.forEach { modelId ->
            assertEquals("expected VIDEO for $modelId", MediaKind.VIDEO, inferMediaKind(modelId))
            assertTrue("expected media recommendation for $modelId", looksLikeMediaGenerationModel(modelId))
        }

        val imageIds = listOf(
            "gpt-image-2.5-sunburst",
            "dall-e-3",
            "flux-pro",
            "gemini-2.5-flash-image",
            "wan2.6-image",
            "grok-imagine-image",
        )
        imageIds.forEach { modelId ->
            assertEquals("expected IMAGE for $modelId", MediaKind.IMAGE, inferMediaKind(modelId))
            assertTrue("expected media recommendation for $modelId", looksLikeMediaGenerationModel(modelId))
        }

        assertFalse(looksLikeMediaGenerationModel("gpt-4o"))
        assertFalse(looksLikeMediaGenerationModel("gemini-2.5-pro"))
        assertFalse(looksLikeMediaGenerationModel("text-embedding-3-large"))
    }

    @Test
    fun listModelsFetchesOpenRouterMediaEndpointsAndOpenAiModels() = runBlocking {
        val openRouterClient = fakeClient { request ->
            when (request.url.encodedPath) {
                "/api/v1/videos/models" -> jsonResponse(
                    request,
                    """{"data":[{"id":"google/veo-3","name":"Veo 3"}]}""",
                )
                "/api/v1/images/models" -> jsonResponse(
                    request,
                    """{"data":[{"id":"black-forest-labs/flux.2-pro","name":"FLUX.2 Pro"}]}""",
                )
                else -> error("unexpected path: ${request.url.encodedPath}")
            }
        }
        val manager = MediaGenerationManager(openRouterClient)
        val openRouterModels = manager.listModels(
            MediaGenerationProviderSetting.OpenRouter(apiKey = "or-test")
        ).getOrThrow().associateBy { it.modelId }
        assertEquals(MediaKind.VIDEO, openRouterModels["google/veo-3"]?.kind)
        assertEquals(MediaKind.IMAGE, openRouterModels["black-forest-labs/flux.2-pro"]?.kind)

        val openAiClient = fakeClient { request ->
            assertEquals("/v1/models", request.url.encodedPath)
            jsonResponse(
                request,
                """{"data":[{"id":"sora-2"},{"id":"gpt-image-1"},{"id":"gpt-4o"}]}""",
            )
        }
        val openAiModels = MediaGenerationManager(openAiClient).listModels(
            MediaGenerationProviderSetting.OpenAI(apiKey = "sk-test", baseUrl = "https://example.com/v1")
        ).getOrThrow().associateBy { it.modelId }
        assertEquals(MediaKind.VIDEO, openAiModels["sora-2"]?.kind)
        assertEquals(true, openAiModels["sora-2"]?.isRecommendedMedia)
        assertEquals(MediaKind.IMAGE, openAiModels["gpt-image-1"]?.kind)
        assertEquals(true, openAiModels["gpt-image-1"]?.isRecommendedMedia)
        assertEquals(false, openAiModels["gpt-4o"]?.isRecommendedMedia)
    }
}
