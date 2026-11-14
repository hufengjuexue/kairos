package me.rerere.rikkahub.data.ai.transformers

import me.rerere.ai.provider.DecisionAnswer
import me.rerere.ai.provider.Model
import me.rerere.ai.provider.ModelType
import me.rerere.ai.provider.ProviderSetting
import me.rerere.rikkahub.data.ai.resolveDecisionModel
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.model.MemoryFile
import me.rerere.ai.ui.UIMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryVectorTailTransformerTest {
    private val sampleChunks = listOf(
        MemoryVectorTailTransformer.RecallChunk(
            namespace = "mem_chunk",
            path = "/topics/life.md",
            title = "生活日常",
            order = 0,
            text = "平时喜欢喝冰美式和深夜写 Kotlin 代码。",
        ),
        MemoryVectorTailTransformer.RecallChunk(
            namespace = "mem_chunk",
            path = "/topics/fetish.md",
            title = "衣着偏好",
            order = 1,
            text = "在实验室那次穿着白袜和百褶裙。",
        ),
        MemoryVectorTailTransformer.RecallChunk(
            namespace = "dialog_chunk",
            path = "/history/dialogue",
            title = "早期对话回顾",
            order = 2,
            text = "用户: 上次我们约好去海边看日出。\n角色: 好，我记得带防晒。",
        ),
    )

    @Test
    fun `lexical fallback recalls relevant Chinese and English chunks in order`() {
        val hits = MemoryVectorTailTransformer.recallByLexical(
            query = "还记得实验室和白袜那件事吗？",
            chunks = sampleChunks,
        )

        assertEquals(1, hits.size)
        assertEquals("/topics/fetish.md", hits.first().path)
    }

    @Test
    fun `lexical fallback returns empty for unrelated query`() {
        val hits = MemoryVectorTailTransformer.recallByLexical(
            query = "量子纠缠超导实验报告",
            chunks = sampleChunks.take(1),
        )

        assertTrue(hits.isEmpty())
    }

    @Test
    fun `rankDecisionAnswers filters low scores and preserves path-order sorting`() {
        val questions = MemoryVectorTailTransformer.buildDecisionQuestions(sampleChunks)
        assertEquals(setOf("c_0", "c_1", "c_2"), questions.keys)

        val ranked = MemoryVectorTailTransformer.rankDecisionAnswers(
            candidates = sampleChunks,
            answers = mapOf(
                "c_0" to DecisionAnswer(score = 0.4, confidence = 0.9),
                "c_1" to DecisionAnswer(score = 2.7, confidence = 0.85),
                "c_2" to DecisionAnswer(score = 1.9, confidence = 0.8),
            ),
        )

        assertEquals(listOf("/history/dialogue", "/topics/fetish.md"), ranked.map { it.path })
    }

    @Test
    fun `parseListwiseIndices extracts valid indices handles NONE and rejects noise`() {
        assertEquals(
            listOf(2, 0),
            MemoryVectorTailTransformer.parseListwiseIndices("2, 0, 99, 2", candidateCount = 3),
        )
        assertEquals(
            emptyList<Int>(),
            MemoryVectorTailTransformer.parseListwiseIndices("NONE", candidateCount = 3),
        )
        assertNull(
            MemoryVectorTailTransformer.parseListwiseIndices("sorry I cannot decide", candidateCount = 3),
        )
    }

    @Test
    fun `buildCoarseCandidatePool unions embedding and lexical without duplicates`() {
        val pool = MemoryVectorTailTransformer.buildCoarseCandidatePool(
            query = "冰美式 白袜",
            chunks = sampleChunks,
            scoredEmbed = listOf(
                sampleChunks[0] to 0.5f,
                sampleChunks[1] to 0.05f,
            ),
            threshold = 0.25f,
        )

        assertEquals(2, pool.size)
        assertTrue(sampleChunks[0] in pool)
        assertTrue(sampleChunks[1] in pool)
    }

    @Test
    fun `resolveDecisionModel picks explicit chat or decision model and auto-picks decision only`() {
        val chatModel = Model(modelId = "gemini-2.5-flash-lite", displayName = "Flash Lite", type = ModelType.CHAT)
        val decisionModel = Model(modelId = "gemini-decision-1", displayName = "Decision 1", type = ModelType.DECISION)
        val google = ProviderSetting.Google(
            name = "Google",
            enabled = true,
            models = listOf(chatModel, decisionModel),
        )
        val googleChatOnly = ProviderSetting.Google(
            name = "Google Chat Only",
            enabled = true,
            models = listOf(chatModel),
        )

        // 未指定时自动发现 Decision 模型，不擅自劫持普通 Chat 模型
        assertEquals(
            decisionModel.id,
            Settings(providers = listOf(google)).resolveDecisionModel()?.second?.id,
        )
        assertNull(
            Settings(providers = listOf(googleChatOnly)).resolveDecisionModel(),
        )

        // 显式指定轻量 Chat 模型作精排时正常解析
        assertEquals(
            chatModel.id,
            Settings(providers = listOf(googleChatOnly), vectorDecisionModelId = chatModel.id)
                .resolveDecisionModel()?.second?.id,
        )
    }

    @Test
    fun `chunkOverflowDialogue preserves full tail of long RP turns without 420-char truncation`() {
        val tailMarker = "TAIL_CLIMAX_RESOLUTION_KEEP_INTACT"
        val longBody = "开场铺垫。".repeat(120) + "\n\n" + "转折推进。".repeat(120) + "\n\n" + tailMarker
        val chunks = MemoryVectorTailTransformer.chunkOverflowDialogue(
            overflowMessages = listOf(UIMessage.assistant(longBody)),
            assistantName = "温既白",
        )

        assertTrue(chunks.isNotEmpty())
        assertTrue(chunks.joinToString("\n") { it.text }.contains(tailMarker))
    }

    @Test
    fun `chunkMemoryFiles keeps mid-sized sections intact and preserves heading when splitting oversized sections`() {
        val midSection = "## 阶段演变\n" + "- [2026-10] 早期边界规则。\n".repeat(30) // ~600-900 chars, stays intact
        val hugeSection = "## 长期脉络\n" + List(65) { i -> "- [2026-11-${(i % 28) + 1}] 第 $i 条连续演化记录内容，用来测试超过 2200 字后的分段与标题保留。\n\n" }.joinToString("")
        val file = MemoryFile(
            path = "/topics/relationship.md",
            content = "---\nname: relationship\ntitle: 关系动态\n---\n\n$midSection\n\n$hugeSection",
            updatedAt = 0,
        )

        val chunks = MemoryVectorTailTransformer.chunkMemoryFiles(listOf(file))

        assertTrue(chunks.any { it.text.contains("## 阶段演变") && it.text.count { c -> c == '\n' } >= 25 })
        val longChunks = chunks.filter { it.text.contains("第 ") }
        assertTrue(longChunks.size >= 2)
        assertTrue(longChunks.all { it.text.startsWith("## 长期脉络\n") })
    }
}
