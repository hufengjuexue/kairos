package me.rerere.rikkahub.data.db.migrations

import me.rerere.rikkahub.data.model.MemoryFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class Migration_27_28_Test {
    @Test
    fun `legacy memories become one core line each`() {
        val core = legacyMemoriesToProfile(
            listOf("User prefers brief replies", "  ", "Name is Lin\nLives in Shenzhen")
        )!!

        assertEquals(
            """
                ---
                name: core
                description: Who the user is
                ---

                - [stated] User prefers brief replies
                - [stated] Name is Lin Lives in Shenzhen

            """.trimIndent(),
            core,
        )
        assertEquals("Who the user is", MemoryFile(MemoryFile.CORE_PATH, core, 0).description)
    }

    @Test
    fun `nothing is created when there are no usable memories`() {
        assertNull(legacyMemoriesToProfile(emptyList()))
        assertNull(legacyMemoriesToProfile(listOf("", "\n")))
    }
}
