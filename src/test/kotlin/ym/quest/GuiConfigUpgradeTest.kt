package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.GuiConfigUpgrade
import java.nio.file.Files
import java.nio.file.Path

class GuiConfigUpgradeTest {
    @TempDir lateinit var root: Path
    private val bundled get() = Files.readString(Path.of("src/main/resources/gui/main.yml"))
    private fun put(name: String, raw: String) { val file=root.resolve(name);Files.createDirectories(file.parent);Files.writeString(file,raw) }
    private fun yaml(raw: String) = YamlConfiguration().apply { loadFromString(raw) }
    private val chapter = """
        # 自定义布局和注释
        schema-version: 1
        title: '<#7DD3FC>测试章节'
        unavailable-item: 'minecraft:barrier'
        custom: '保留未知值'
        Shape:
          - 'SQQQQQQQC'
          - 'P###B###N'
        components:
          '#':
            item: 'minecraft:glass'
            name: ''
          S:
            item: 'minecraft:book'
            action: 'chapter-summary'
          Q:
            item: 'minecraft:book'
            action: 'quest'
            lore:
              - '<status>'
              - '<#94A3B8>点击查看条件及任务奖励'
          C:
            item: 'minecraft:emerald'
            action: 'chapter-claim'
          B:
            item: 'minecraft:arrow'
            action: 'back'
          P:
            item: 'minecraft:arrow'
            action: 'previous'
          N:
            item: 'minecraft:arrow'
            action: 'next'
          V:
            item: 'minecraft:chest'
            action: 'chapter-preview'
          W:
            item: 'minecraft:chest'
            action: 'mailbox'
    """.trimIndent()+"\n"
    @Test fun `chapter layout replaces old center and retired screens are backed up without restoring unused buttons`() {
        val old="# 旧任务中心\nschema-version: 1\nplayer-access: true\n"
        put("gui/main.yml",old);put("gui/chapter-detail.yml",chapter);put("gui/detail.yml","schema-version: 1\n")
        GuiConfigUpgrade.migrate(root,bundled)
        val raw=Files.readString(root.resolve("gui/main.yml"));val y=yaml(raw)
        assertEquals(2,y.getInt("schema-version"));assertTrue(y.getBoolean("player-access"))
        assertEquals(yaml(chapter).getStringList("Shape"),y.getStringList("Shape"))
        assertTrue(raw.contains("# 自定义布局和注释"));assertEquals("保留未知值",y.getString("custom"))
        assertFalse(y.contains("components.V"));assertFalse(y.contains("components.W"))
        assertTrue(y.getStringList("components.Q.lore").containsAll(listOf("<description>","<objectives>","<rewards>")))
        assertFalse(raw.contains("点击查看条件"))
        assertEquals(old,Files.readString(root.resolve(".migrations/gui-v2/0.before")))
        assertFalse(Files.exists(root.resolve("gui/detail.yml")));assertFalse(Files.exists(root.resolve("gui/chapter-detail.yml")))
        GuiConfigUpgrade.migrate(root,bundled);assertEquals(raw,Files.readString(root.resolve("gui/main.yml")))
        // Resume an interrupted source retirement after publishing the new main file.
        put("gui/chapter-detail.yml",chapter);Files.delete(root.resolve(".migrations/gui-v2/complete"))
        GuiConfigUpgrade.migrate(root,bundled)
        assertFalse(Files.exists(root.resolve("gui/chapter-detail.yml")))
        assertEquals(raw,Files.readString(root.resolve("gui/main.yml")))
    }
    @Test fun `indentless lists and hand moved main retain their custom layout`() {
        val moved=chapter.replace("      - ","    - ")
        put("gui/main.yml",moved)
        GuiConfigUpgrade.migrate(root,bundled)
        assertEquals(yaml(chapter).getStringList("Shape"),yaml(Files.readString(root.resolve("gui/main.yml"))).getStringList("Shape"))
    }
    @Test fun `future schemas and new main with legacy detail conflict before any mutation`() {
        for(main in listOf(bundled,"schema-version: 99\n")) {
            put("gui/main.yml",main);put("gui/chapter-detail.yml",chapter)
            assertThrows(IllegalArgumentException::class.java) { GuiConfigUpgrade.migrate(root,bundled) }
            assertEquals(main,Files.readString(root.resolve("gui/main.yml")))
            assertEquals(chapter,Files.readString(root.resolve("gui/chapter-detail.yml")))
        }
    }
}
