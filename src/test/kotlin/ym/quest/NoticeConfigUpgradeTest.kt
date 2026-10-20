package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.NoticeConfigUpgrade
import ym.quest.config.NoticeFiles
import java.nio.file.Files
import java.nio.file.Path

class NoticeConfigUpgradeTest {
    @TempDir lateinit var root: Path
    private val bundled=Files.readString(Path.of("src/main/resources/提示.yml"))
    @Test fun `old completion channels become chat only without losing custom comments and options`() {
        val file=root.resolve("提示.yml")
        val old="# 自定义注释\n"+bundled.replace("schema-version: 2","schema-version: 1")
            .replace("chat: true","chat: false # 保留行尾注释").replace("title: false","title: true")+"custom: '保留'\n"
        Files.writeString(file,old)
        NoticeConfigUpgrade.migrate(root)
        val next=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(next) }
        for(event in listOf("task-complete","chapter-complete")) {
            val profile=NoticeFiles.parse(y).getValue(event)
            assertTrue(profile.chat); assertFalse(profile.actionBar); assertFalse(profile.title); assertNull(profile.sound)
        }
        assertTrue(next.startsWith("# 自定义注释\n")); assertTrue(next.contains("# 保留行尾注释"))
        assertEquals("保留",y.getString("custom"));assertTrue(y.getBoolean("chapter-start.title"))
        assertEquals(old,Files.readString(root.resolve(".migrations/notices-v2/0.before")))
        NoticeConfigUpgrade.migrate(root);assertEquals(next,Files.readString(file))
    }
    @Test fun `version two custom settings are retained and unsupported versions fail without replacement`() {
        val file=root.resolve("提示.yml")
        val custom=bundled.replace("chat: true","chat: false")
        Files.writeString(file,custom);NoticeConfigUpgrade.migrate(root);assertEquals(custom,Files.readString(file))
        val future=bundled.replace("schema-version: 2","schema-version: 3")
        Files.writeString(file,future)
        assertThrows(IllegalStateException::class.java) { NoticeConfigUpgrade.migrate(root) }
        assertEquals(future,Files.readString(file))
    }
}
