package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.hud.HudSettings
import java.nio.file.Files
import java.nio.file.Path

class HudColorMapTest {
    @TempDir lateinit var root: Path
    private fun config(extra: String): Path {
        val file=root.resolve("quest.yml")
        Files.writeString(file,"""
            schema-version: 1
            enabled: true
            tracker-layout: 'quest_tracker'
            completion-layout: 'quest_complete'
            refresh-ticks: 20
            task-duration-ticks: 72
            chapter-duration-ticks: 112
            fade-ticks: 6
            queue-limit: 4
            text-width: 26
            text:
              chapter: '[{value}](color=#725945)'
              task: '[{value}](color=#9C491D)'
              objective: '[{value}](color=#725945)'
              heading: '[{value}](color=#FFF4E0)'
              name: '[{value}](color=#9C491D)'
              detail: '[{value}](color=#725945)'
              progress-text: '[{value}](color=#9C491D)'
        """.trimIndent()+"\n"+extra)
        return file
    }
    @Test fun `optional color map preserves old config and normalizes RGB keys`() {
        assertTrue(HudSettings.load(config("")).colorMap.isEmpty())
        val settings=HudSettings.load(config("color-map:\n  '#e2e8f0': '#725945'\n"))
        assertEquals(mapOf("#E2E8F0" to "#725945"),settings.colorMap)
    }
    @Test fun `invalid color map reports file key and value`() {
        val error=assertThrows(IllegalArgumentException::class.java) { HudSettings.load(config("color-map:\n  '#E2E8F0': 'brown'\n")) }
        assertTrue(error.message!!.contains("quest.yml:color-map.#E2E8F0=brown"))
        assertThrows(IllegalArgumentException::class.java) { HudSettings.load(config("color-map: 'invalid'\n")) }
    }
}
