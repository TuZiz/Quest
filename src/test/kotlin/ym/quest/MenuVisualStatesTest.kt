package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.config.QuestFiles

class MenuVisualStatesTest {
    private fun menu(extra: String)=YamlConfiguration().apply { loadFromString("""
        schema-version: 1
        title: '<#89F7FE>设置'
        unavailable-item: 'minecraft:barrier'
        Shape:
          - '    H    '
        components:
          H:
            item: 'minecraft:spyglass'
            action: 'toggle-hud'
    """.trimIndent()+"\n"+extra) }
    @Test fun `state visuals require a complete pair on an existing toggle`() {
        val y=menu("    item-on: 'example:on'\n    item-off: 'example:off'\n    pack-only: true\n")
        assertDoesNotThrow { QuestFiles.validateMenu("gui/settings.yml",y) }
        y.set("components.H.item-off",null)
        assertThrows(IllegalArgumentException::class.java) { QuestFiles.validateMenu("gui/settings.yml",y) }
        y.set("components.H.item-off","example:off");y.set("components.H.action","close")
        assertThrows(IllegalArgumentException::class.java) { QuestFiles.validateMenu("gui/settings.yml",y) }
    }
    @Test fun `pack only flag is a boolean`() {
        val y=menu("    pack-only: 'yes'\n")
        assertThrows(IllegalArgumentException::class.java) { QuestFiles.validateMenu("gui/settings.yml",y) }
    }
    @Test fun `pack item preserves a vanilla fallback and rejects invalid IDs`() {
        val y=menu("    pack-item: 'quest_settings:hitbox'\n")
        assertDoesNotThrow { QuestFiles.validateMenu("gui/settings.yml",y) }
        y.set("components.H.pack-item",42)
        assertThrows(IllegalArgumentException::class.java) { QuestFiles.validateMenu("gui/settings.yml",y) }
        y.set("components.H.pack-item","")
        assertThrows(IllegalArgumentException::class.java) { QuestFiles.validateMenu("gui/settings.yml",y) }
    }
}
