package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import java.nio.file.Files
import java.nio.file.Path

class ChapterResourcesTest {
    @TempDir lateinit var root: Path
    private fun yaml(file: String) = YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/$file"))) }
    @Test fun `resource layout migration preserves custom content is idempotent and rejects conflicts`() {
        val paths=mapOf("gui/章节.yml" to "gui/chapters.yml",
            "gui/章节详情.yml" to "gui/chapter-detail.yml", "章节.yml" to "chapters/章节.yml")
        paths.keys.forEach { name ->
            val file=root.resolve(name);Files.createDirectories(file.parent)
            Files.writeString(file,"# 自定义中文注释\nschema-version: 1\nunknown: 保留 $name\n")
        }
        repeat(2) { ResourceUpgrade.relocateLayout(root) }
        paths.forEach { (old,next) ->
            assertFalse(Files.exists(root.resolve(old)))
            assertEquals("# 自定义中文注释\nschema-version: 1\nunknown: 保留 $old\n",Files.readString(root.resolve(next)))
        }
        Files.writeString(root.resolve("章节.yml"),"旧配置")
        assertThrows(IllegalArgumentException::class.java) { ResourceUpgrade.relocateLayout(root) }
        assertEquals("旧配置",Files.readString(root.resolve("章节.yml")))
        assertTrue(Files.readString(root.resolve("chapters/章节.yml")).contains("unknown: 保留"))
    }
    @Test fun `default chapter contains exactly the three existing tasks and notices support every channel switch`() {
        val chapter=ChapterFiles.parse(yaml("chapters/第一章·星海初醒.yml"))
        assertEquals("sky_awakening",chapter.id)
        assertEquals(listOf("sky_start","first_fuel","first_resources"),chapter.quests)
        assertTrue(chapter.story.isNotEmpty()); assertTrue(chapter.rewards.isNotEmpty())
        val profiles=NoticeFiles.parse(yaml("提示.yml"))
        assertTrue(profiles.getValue("chapter-complete").title)
        assertNotNull(profiles.getValue("chapter-complete").sound)
        val disabled=yaml("提示.yml")
        NoticeFiles.events.forEach { event -> listOf("chat","action-bar","title","sound-enabled").forEach { disabled.set("$event.$it",false) } }
        assertTrue(NoticeFiles.parse(disabled).values.all { !it.chat && !it.actionBar && !it.title && it.sound == null })
        disabled.set("next-task.pitch",5)
        assertThrows(IllegalArgumentException::class.java) { NoticeFiles.parse(disabled) }
        val invalid=yaml("chapters/第一章·星海初醒.yml"); invalid.set("rewards",listOf(mapOf("item" to "minecraft:diamond","amount" to 0)))
        assertThrows(IllegalArgumentException::class.java) { ChapterFiles.parse(invalid) }
    }
    @Test fun `legacy filename migration and language append are idempotent preserve comments and unknown fields`() {
        Files.createDirectories(root.resolve("quests"))
        val original="# 自定义注释\nschema-version: 1\nid: sky_start\nunknown: 保留\n"
        Files.writeString(root.resolve("quests/sky_start.yml"),original)
        repeat(2) { ResourceUpgrade.renameQuests(root) }
        assertEquals(original,Files.readString(root.resolve("quests/星海启航.yml")))
        assertFalse(Files.exists(root.resolve("quests/sky_start.yml")))
        val language=root.resolve("中文.yml")
        Files.writeString(language,"# 自定义中文注释\nschema-version: 1\nunknown: 原值\n")
        val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
        ResourceUpgrade.appendLanguage(language,bundled)
        val upgraded=Files.readString(language)
        ResourceUpgrade.appendLanguage(language,bundled)
        assertEquals(upgraded,Files.readString(language))
        assertTrue(upgraded.startsWith("# 自定义中文注释\nschema-version: 1\nunknown: 原值\n"))
        val y=YamlConfiguration().apply { loadFromString(upgraded) }
        assertTrue(y.isString("notices.chapter-complete.title"));assertTrue(y.isString("chapter-status.COMPLETE"))
        Files.writeString(root.resolve("quests/sky_start.yml"),"冲突文件")
        assertThrows(IllegalArgumentException::class.java) { ResourceUpgrade.renameQuests(root) }
        assertEquals(original,Files.readString(root.resolve("quests/星海启航.yml")))
        assertEquals("冲突文件",Files.readString(root.resolve("quests/sky_start.yml")))
    }
    @Test fun `wording upgrade is narrow and future resource schemas are never rewritten`() {
        Files.createDirectories(root.resolve("gui"))
        val detail=root.resolve("gui/detail.yml")
        Files.writeString(detail,"# 中文注释\nschema-version: 1\ncustom: 保留\nlore:\n  - '前置任务领取后自动解锁下一任务。'\n")
        ResourceUpgrade.updateProgressionText(root)
        val migrated=Files.readString(detail)
        ResourceUpgrade.updateProgressionText(root)
        assertEquals(migrated,Files.readString(detail))
        assertTrue(migrated.contains("前置目标完成即解锁，无需立即领奖。"))
        assertTrue(migrated.startsWith("# 中文注释\nschema-version: 1\ncustom: 保留"))
        val future=root.resolve("语言.yml")
        val original="# 未来版本\nschema-version: 99\nunknown: 保留\n"
        Files.writeString(future,original)
        assertThrows(IllegalArgumentException::class.java) {
            ResourceUpgrade.appendLanguage(future,Files.readString(Path.of("src/main/resources/lang/zh_cn.yml")))
        }
        assertEquals(original,Files.readString(future))
    }
}
