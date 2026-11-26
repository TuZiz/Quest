package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.TrialRewardUpgrade
import java.nio.file.Files
import java.nio.file.Path

class TrialRewardUpgradeTest {
    @TempDir lateinit var root: Path
    @Test fun `stock fuel migrates to sword with backup and custom rewards remain untouched`() {
        val current=Files.readString(Path.of("src/main/resources/quests/第一章/支线/支1-04 石址试炼.yml"))
            .replace(Regex("(?m)^  - item: 'minecraft:baked_potato'[^\\r\\n]*\\r?\\n    amount: 8\\r?\\n?"),"")
        val old=current.replace("'itemhub:星芽:短剑'","'genlite:fuel'").replace("amount: 1","amount: 8")+"\n# 保留注释\ncustom: '保留'\n"
        val file=root.resolve("quests/trial.yml")
        Files.createDirectories(file.parent);Files.writeString(file,old)
        val custom=root.resolve("quests/custom.yml")
        val customText=old.replace("amount: 8","amount: 16")
        Files.writeString(custom,customText)
        TrialRewardUpgrade.migrate(root)
        val after=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(after) }
        assertEquals(listOf(mapOf("item" to "itemhub:星芽:短剑","amount" to 1)),y.getMapList("rewards"))
        assertEquals(YamlConfiguration().apply { loadFromString(old) }.getMapList("objectives"),y.getMapList("objectives"))
        assertTrue(after.contains("# 保留注释\ncustom: '保留'"))
        assertEquals(old,Files.readString(root.resolve(".migrations/trial-sword-reward-v1/0.before")))
        assertEquals(customText,Files.readString(custom))
        TrialRewardUpgrade.migrate(root)
        assertEquals(after,Files.readString(file))
    }
}
