package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.bukkit.configuration.file.YamlConfiguration
import ym.quest.config.*
import java.nio.file.Files
import java.nio.file.Path

class TutorialRpgRewardsUpgradeTest {
    @TempDir lateinit var root: Path
    private fun put(name: String,raw: String) {val p=root.resolve(name);Files.createDirectories(p.parent);Files.writeString(p,raw)}
    @Test fun `removes vanilla defaults preserves machine reward and database and archives old configuration`() {
        val chapter=Files.readString(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}")).substringBefore("rewards:")+"rewards:\n  - item: 'minecraft:diamond'\n    amount: 2\n"
        put(ChapterConfigUpgrade.defaultFile,chapter)
        put("quests/第一章/星海启航.yml","schema-version: 2\nid: sky_start\n# 保留\nrewards:\n  - item: 'minecraft:bread'\n    amount: 8\n  - item: 'genlite:cobblestone_generator'\n    amount: 1\n    unless-generator-exists: true\n")
        put("data/quest.db","old claims and parcels")
        repeat(2) {TutorialRpgRewardsUpgrade.migrate(root)}
        val raw=Files.readString(root.resolve("quests/第一章/星海启航.yml"))
        val y=YamlConfiguration().apply {loadFromString(raw)}
        assertEquals("genlite:cobblestone_generator",y.getMapList("rewards").single()["item"])
        assertEquals(true,y.getMapList("rewards").single()["unless-generator-exists"])
        assertTrue(raw.contains("# 保留"))
        assertEquals("old claims and parcels",Files.readString(root.resolve("data/quest.db")))
        val next=Files.readString(root.resolve(ChapterConfigUpgrade.defaultFile))
        assertFalse(next.contains("minecraft:diamond"));assertTrue(next.contains("genlite:mechanical_parts"))
    }
    @Test fun `every bundled first chapter reward uses registered custom item namespaces`() {
        val paths=QuestTaskFiles.files(Path.of("src/main/resources/quests/第一章"))+listOf(Path.of("src/main/resources/${ChapterConfigUpgrade.defaultFile}"))
        paths.forEach { path ->
            val raw=Files.readString(path);val y=YamlConfiguration().apply {loadFromString(raw)}
            assertTrue(y.getMapList("rewards").isNotEmpty())
            assertTrue(y.getMapList("rewards").all {(it["item"] as String).substringBefore(':') in setOf("genlite","currency")},path.toString())
        }
    }
}
