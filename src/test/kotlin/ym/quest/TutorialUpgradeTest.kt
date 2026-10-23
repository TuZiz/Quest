package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import java.nio.file.Files
import java.nio.file.Path

class TutorialUpgradeTest {
    @TempDir lateinit var root: Path
    private fun resource(path: String)=Files.readString(Path.of("src/main/resources/$path"))
    private fun put(path: String,raw: String) {val p=root.resolve(path);Files.createDirectories(p.parent);Files.writeString(p,raw)}
    @Test fun `adds fourteen tasks with independent branches while preserving old customized rewards comments and database bytes`() {
        val legacy=resource(ChapterConfigUpgrade.defaultFile).substringBefore("  - quest: first_harvest")+"rewards:\n  - item: minecraft:emerald\n    amount: 9\n# 中文用户注释\nunknown: '保留'\n"
        put(ChapterConfigUpgrade.defaultFile,legacy)
        val old=resource("quests/第一章/第一批资源.yml").replace("amount: 2","amount: 7")
        put("quests/第一章/第一批资源.yml",old)
        put("data/quest.db","existing database sentinel")
        repeat(2) {TutorialUpgrade.migrate(root,::resource)}
        val raw=Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile))
        val ch=ChapterFiles.parse(YamlConfiguration().apply {loadFromString(raw)})
        assertEquals(18,ch.quests.size);assertEquals(14,ch.requiredQuests.size);assertEquals(4,ch.optionalQuests.size)
        assertTrue(raw.contains("# 中文用户注释"));assertTrue(raw.contains("unknown: '保留'"))
        assertEquals(9,ch.rewards.single().amount)
        assertEquals(old,Files.readString(root.resolve("quests/第一章/第一批资源.yml")))
        assertEquals("existing database sentinel",Files.readString(root.resolve("data/quest.db")))
        assertTrue(Files.exists(root.resolve(".migrations/chapter-one-tutorial-v1/complete")))
        assertEquals(15,QuestTaskFiles.files(root.resolve("quests")).size)
        val once=Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile));TutorialUpgrade.migrate(root,::resource)
        assertEquals(once,Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile)))
    }
    @Test fun `conflicting new task path aborts before publishing chapter or any task`() {
        put(ChapterConfigUpgrade.defaultFile,resource(ChapterConfigUpgrade.defaultFile))
        put("quests/第一章/收获的喜悦.yml","schema-version: 2\nid: custom\n")
        assertThrows(IllegalArgumentException::class.java) {TutorialUpgrade.migrate(root,::resource)}
        assertFalse(Files.exists(root.resolve("quests/第一章/机械革新.yml")))
        assertFalse(Files.exists(root.resolve("教学.yml")))
    }
}
