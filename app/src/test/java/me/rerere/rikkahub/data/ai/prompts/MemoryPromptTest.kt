package me.rerere.rikkahub.data.ai.prompts

import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryPromptTest {
    private val core = MemoryFile(MemoryFile.CORE_PATH, "- [stated] Name is Lin\n", updatedAt = 0)
    private val food = MemoryFile(
        path = "/topics/food.md",
        content = """
            ---
            name: food
            description: What the user eats and avoids
            aliases: [diet, 饮食]
            ---

            - [stated] Allergic to peanuts
        """.trimIndent(),
        updatedAt = 0,
    )

    private fun String.block(tag: String) = substringAfter("<$tag").substringAfter(">").substringBefore("</$tag>").trim()

    @Test
    fun `core is injected in full with its version and other files only appear in the listing`() {
        val prompt = buildMemoryPrompt(listOf(core, food))

        assertTrue(prompt.contains("<core version=\"${core.version}\">"))
        assertEquals("- [stated] Name is Lin", prompt.block("core"))
        assertFalse(prompt.contains("Under 300 words"))
        assertFalse(prompt.contains("approaches its size limit"))

        val listing = prompt.block("memory_listing")
        assertEquals("/topics/food.md | What the user eats and avoids | aliases: diet, 饮食", listing)
        assertFalse(listing.contains("updated:"))
        assertFalse(listing.contains(MemoryFile.CORE_PATH))
        assertFalse(prompt.contains("Allergic to peanuts"))
    }

    @Test
    fun `legacy profile and preferences paths still populate core and state blocks`() {
        val legacyProfile = MemoryFile(MemoryFile.LEGACY_PROFILE_PATH, "- [stated] Legacy name\n", updatedAt = 0)
        val legacyPrefs = MemoryFile(MemoryFile.LEGACY_PREFERENCES_PATH, "- [stated] Legacy voice\n", updatedAt = 0)

        val prompt = buildMemoryPrompt(listOf(legacyProfile, legacyPrefs, food))

        assertEquals("- [stated] Legacy name", prompt.block("core"))
        assertEquals("- [stated] Legacy voice", prompt.block("state"))
        val listing = prompt.block("memory_listing")
        assertFalse(listing.contains(MemoryFile.LEGACY_PROFILE_PATH))
        assertFalse(listing.contains(MemoryFile.LEGACY_PREFERENCES_PATH))
    }

    @Test
    fun `missing files are shown as empty and creatable`() {
        val prompt = buildMemoryPrompt(emptyList())

        assertTrue(prompt.contains("<state version=\"new\">"))
        assertEquals("(empty)", prompt.block("state"))
        assertEquals("(no files yet)", prompt.block("memory_listing"))
    }

    @Test
    fun `consolidation input carries the memory state and each conversation's new turns`() {
        val input = buildMemoryConsolidationInput(
            files = listOf(core, food),
            conversations = listOf(
                MemoryConsolidationConversation(
                    id = "c1",
                    title = "Moving <to> \"Hangzhou\"",
                    recentContext = "",
                    newTurns = "[USER #4]: I moved to Hangzhou",
                ),
            ),
        )

        assertEquals("- [stated] Name is Lin", input.block("core"))
        assertTrue(input.block("memory_listing").startsWith("/topics/food.md"))
        // 标题里的引号和尖括号不能破坏标签
        assertTrue(input.contains("<conversation id=\"c1\" title=\"Moving  to   Hangzhou\">"))
        assertEquals("(none)", input.block("recent_context"))
        assertEquals("[USER #4]: I moved to Hangzhou", input.block("new_turns"))
    }

    @Test
    fun `consolidation input says so when there is nothing new`() {
        val input = buildMemoryConsolidationInput(files = listOf(core), conversations = emptyList())

        assertTrue(input.endsWith("(no new conversation turns)"))
        assertFalse(input.contains("<conversation"))
    }

    @Test
    fun `whole-store tidying is only asked for in a full review`() {
        assertFalse(buildMemoryConsolidationPrompt(fullReview = false).contains("## Full review"))
        assertTrue(buildMemoryConsolidationPrompt(fullReview = true).contains("## Full review"))
    }

    @Test
    fun `prompts do not force bracketed stated tags or arbitrary two-record caps`() {
        val memoryPrompt = buildMemoryPrompt(emptyList())
        val consolidationPrompt = buildMemoryConsolidationPrompt(fullReview = false)

        assertFalse(memoryPrompt.contains("[stated]"))
        assertFalse(memoryPrompt.contains("topic or preference files"))
        assertFalse(consolidationPrompt.contains("[stated]"))
        assertFalse(consolidationPrompt.contains("zero to two records"))
        assertTrue(memoryPrompt.contains("## Factual & canon grounding"))
        assertTrue(consolidationPrompt.contains("hot-cold sync"))
    }
}
