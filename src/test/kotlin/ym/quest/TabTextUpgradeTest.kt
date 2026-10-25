package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.TabTextUpgrade
import java.nio.file.Files
import java.nio.file.Path

class TabTextUpgradeTest {
    @TempDir lateinit var root: Path

    @Test fun `old tab defaults migrate with backup and preserve comments custom text and unknown fields`() {
        val file=root.resolve("lang/zh_cn.yml")
        Files.createDirectories(file.parent)
        val original="schema-version: 1\n# 自定义注释\ncustom: '保留'\ntab:\n  chapter-line: '<#94A3B8>▪ <chapter>'\n  quest-line: '<#FFD700>✦ <bold><quest></bold>' # 任务名\n  objective-line: '自定义目标 <objective>'\n".replace("\n","\r\n")
        Files.writeString(file,original)
        val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
        TabTextUpgrade.migrate(root,bundled)
        val once=Files.readString(file)
        TabTextUpgrade.migrate(root,bundled)
        assertEquals(once,Files.readString(file))
        assertTrue(once.contains("# 自定义注释") && once.contains("# 任务名"))
        assertFalse(once.replace("\r\n", "").contains('\n'))
        val yaml=YamlConfiguration().apply { loadFromString(once) }
        assertEquals("保留",yaml.getString("custom"))
        assertEquals("自定义目标 <objective>",yaml.getString("tab.objective-line"))
        assertEquals("<#94A3B8>  <chapter>",yaml.getString("tab.chapter-line"))
        assertTrue(yaml.getString("tab.quest-line")!!.contains("<progress>"))
        assertEquals(original,Files.readString(root.resolve(".migrations/tab-main-style-v1/0.before")))
    }
}
