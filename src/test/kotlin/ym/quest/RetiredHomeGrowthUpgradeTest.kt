package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import java.nio.file.Files
import java.nio.file.Path

class RetiredHomeGrowthUpgradeTest {
    @TempDir lateinit var root: Path

    @Test fun `retiring home growth archives definition and chapter entry without changing history or other rewards`() {
        val chapterFile=root.resolve(ChapterConfigUpgrade.defaultFile)
        Files.createDirectories(chapterFile.parent)
        val original=Files.readString(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}"))
            .replace("  - quest: first_trial","  - quest: 'home_growth'\n    requires:\n      - sky_start\n    optional: true\n  - quest: first_trial")+"\n# 保留注释\ncustom: '保留'\n"
        Files.writeString(chapterFile,original)
        val task=root.resolve("quests/第一章/支线/自定义文件名.yml")
        Files.createDirectories(task.parent)
        val retired="schema-version: 2\nid: 'home_growth'\n# 旧任务自定义奖励\nrewards:\n  - item: 'currency:copper_coin'\n    amount: 7\n"
        Files.writeString(task,retired)
        val db=root.resolve("data/quest.db")
        Files.createDirectories(db.parent)
        Files.writeString(db,"history and reward claim sentinel")
        RetiredHomeGrowthUpgrade.migrate(root)
        val once=Files.readString(chapterFile)
        RetiredHomeGrowthUpgrade.migrate(root)
        assertEquals(once,Files.readString(chapterFile))
        assertFalse(Files.exists(task))
        assertFalse("home_growth" in ChapterFiles.parse(YamlConfiguration().apply { loadFromString(once) }).quests)
        assertTrue(once.contains("# 保留注释") && once.contains("custom: '保留'"))
        assertEquals("history and reward claim sentinel",Files.readString(db))
        val backups=Files.walk(root.resolve(".migrations/retire-home-growth-v1")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".before") }.map { Files.readString(it) }.toList()
        }
        assertTrue(original in backups && retired in backups)
    }
}
