package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.*
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

class TutorialCurrencyUpgradeTest {
    @TempDir lateinit var root: Path
    private fun resource(path: String)=Files.readString(Path.of("src/main/resources/$path"))
    private fun put(path: String,text: String) {val p=root.resolve(path);Files.createDirectories(p.parent);Files.writeString(p,text)}
    @Test fun `adds currency without duplicating entries or touching old reward data`() {
        put(ChapterConfigUpgrade.defaultFile,resource(ChapterConfigUpgrade.defaultFile))
        val path="quests/第一章/机械的联系.yml"
        put(path,"schema-version: 2\nid: first_binding\n# 保留用户自定义\ncustom: 99\n")
        put("data/quest.db","old reward data")
        repeat(2) {TutorialCurrencyUpgrade.migrate(root,::resource)}
        val raw=Files.readString(root.resolve(path));val y=YamlConfiguration().apply {loadFromString(raw)}
        assertTrue(raw.contains("# 保留用户自定义"))
        assertEquals(99,y.getInt("custom"))
        assertEquals(1,y.getMapList("rewards").count {it["item"]=="currency:copper_coin"})
        assertEquals("old reward data",Files.readString(root.resolve("data/quest.db")))
    }
    @Test fun `existing item rewards remove overlapping currency and never gain new currency`() {
        put(ChapterConfigUpgrade.defaultFile,resource(ChapterConfigUpgrade.defaultFile))
        val path="quests/第一章/机械的联系.yml"
        put(path,"schema-version: 2\nid: first_binding\nrewards:\n  - item: 'minecraft:bread'\n    amount: 99\n  - item: 'currency:copper_coin'\n    amount: 5\n")
        TutorialCurrencyUpgrade.migrate(root,::resource)
        val raw=Files.readString(root.resolve(path))
        val y=YamlConfiguration().apply {loadFromString(raw)}
        assertEquals(listOf(mapOf("item" to "minecraft:bread","amount" to 99)),y.getMapList("rewards"))
    }
}
