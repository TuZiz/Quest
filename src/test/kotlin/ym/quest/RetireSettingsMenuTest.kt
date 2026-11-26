package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.RetireSettingsMenu
import java.nio.file.Files
import java.nio.file.Path

class RetireSettingsMenuTest {
    @TempDir lateinit var root: Path
    @Test fun `retirement removes entry and screen retaining other configuration and backups`() {
        Files.createDirectories(root.resolve("gui"))
        val old="""
            # preserve custom comment
            schema-version: 2
            title: '<#89F7FE>日志'
            unavailable-item: 'minecraft:structure_void'
            Shape:
              - '    I   B'
            components:
              I:
                item: 'minecraft:comparator'
                action: 'settings'
                name: '<#89F7FE>设置'
              B:
                item: 'minecraft:barrier'
                action: 'close'
                name: '<#89F7FE>关闭'
            custom: 'untouched'
        """.trimIndent()+"\n"
        Files.writeString(root.resolve("gui/chapters.yml"),old)
        Files.writeString(root.resolve("gui/settings.yml"),"# legacy settings\n")
        RetireSettingsMenu.migrate(root)
        assertFalse(Files.exists(root.resolve("gui/settings.yml")))
        val result=Files.readString(root.resolve("gui/chapters.yml"))
        val y=YamlConfiguration().apply { loadFromString(result) }
        assertFalse(y.contains("components.I"))
        assertEquals(listOf("        B"),y.getStringList("Shape"))
        assertEquals("untouched",y.getString("custom"))
        assertTrue(result.contains("# preserve custom comment"))
        assertEquals(old,Files.readString(root.resolve(".migrations/retire-settings-v1/0.before")))
        RetireSettingsMenu.migrate(root)
        assertEquals(result,Files.readString(root.resolve("gui/chapters.yml")))
    }
}
