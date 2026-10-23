package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

class TutorialLevelTwoUpgradeTest {
    @TempDir lateinit var root: Path
    private fun put(name: String,raw: String) { val p=root.resolve(name);Files.createDirectories(p.parent);Files.writeString(p,raw) }
    @Test fun `retired stages archived and default upgrade materials replaced without touching database or custom rewards`() {
        val chapter=Files.readString(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}"))
            .replace("  - quest: more_resources","  - quest: upgrade_three\n    requires:\n      - first_extract\n  - quest: more_resources")
        put(ChapterConfigUpgrade.defaultFile,chapter)
        put("quests/第一章/产能突破.yml","schema-version: 2\nid: upgrade_three\n# 原配置备份\n")
        put("quests/第一章/资源的归宿.yml","schema-version: 2\nid: first_extract\n# 用户注释\nrewards:\n  - item: 'genlite:basic_upgrade_unit'\n    amount: 6\n")
        put("data/quest.db","original database")
        repeat(2) {TutorialLevelTwoUpgrade.migrate(root)}
        assertFalse(Files.exists(root.resolve("quests/第一章/产能突破.yml")))
        val raw=Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile))
        val ch=ChapterFiles.parse(YamlConfiguration().apply {loadFromString(raw)})
        assertEquals(10,ch.requiredQuests.size);assertFalse("upgrade_three" in ch.quests)
        assertTrue(Files.readString(root.resolve("quests/第一章/资源的归宿.yml")).contains("# 用户注释"))
        assertEquals("original database",Files.readString(root.resolve("data/quest.db")))
        assertTrue(Files.walk(root.resolve(".migrations/chapter-one-level-two-v1")).use { s -> s.filter {it.toString().endsWith(".before")}.anyMatch {Files.readString(it).contains("# 原配置备份")} })
    }
    @Test fun `bundled first chapter has copper silver rewards but no gold items or level above two`() {
        val paths=QuestTaskFiles.files(Path.of("src/main/resources/quests/第一章"))
        assertEquals(14,paths.size)
        paths.forEach { path ->
            val y=YamlConfiguration().apply {loadFromString(Files.readString(path))}
            val rewards=y.getMapList("rewards")
            assertTrue(rewards.isNotEmpty())
            assertFalse(rewards.any {(it["item"] as String).startsWith("currency:")} && rewards.any {!(it["item"] as String).startsWith("currency:")})
            assertTrue(y.getMapList("rewards").none {it["item"] in setOf("currency:gold_coin","currency:gold_ingot")})
            y.getMapList("objectives").filter {it["type"]=="genlite.level"}.forEach {assertEquals(2,it["target"])}
        }
    }
}
