package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.StarterFoodUpgrade
import java.nio.file.Files
import java.nio.file.Path

class StarterFoodUpgradeTest {
    @TempDir lateinit var root: Path
    @Test fun `adds food preserving prior rewards comments and unknown fields and repeats safely`() {
        val path=root.resolve("quests/start.yml")
        Files.createDirectories(path.parent)
        val original="""
            schema-version: 2
            id: 'sky_start'
            # keep machine condition
            rewards:
              - item: 'genlite:cobblestone_generator'
                amount: 1
                unless-generator-exists: true
            custom: 'preserved'
        """.trimIndent()+"\n"
        Files.writeString(path,original)
        val trial=root.resolve("quests/trial.yml")
        val trialBefore="schema-version: 2\nid: 'first_trial'\nrewards:\n  - item: 'itemhub:星芽:短剑'\n    amount: 1\n"
        Files.writeString(trial,trialBefore)
        StarterFoodUpgrade.migrate(root)
        val after=Files.readString(path)
        val yaml=YamlConfiguration().apply { loadFromString(after) }
        assertEquals(listOf(mapOf("item" to "genlite:cobblestone_generator","amount" to 1,"unless-generator-exists" to true),
            mapOf("item" to "minecraft:bread","amount" to 8)),yaml.getMapList("rewards"))
        assertEquals("preserved",yaml.getString("custom"))
        assertTrue(after.contains("# keep machine condition"))
        assertEquals(listOf(mapOf("item" to "itemhub:星芽:短剑","amount" to 1),mapOf("item" to "minecraft:baked_potato","amount" to 8)),
            YamlConfiguration().apply { loadFromString(Files.readString(trial)) }.getMapList("rewards"))
        assertEquals(original,Files.readString(root.resolve(".migrations/starter-food-v1/0.before")))
        StarterFoodUpgrade.migrate(root)
        assertEquals(after,Files.readString(path))
    }
    @Test fun `existing custom food amount is never duplicated or overwritten`() {
        val path=root.resolve("quests/start.yml")
        Files.createDirectories(path.parent)
        val original="schema-version: 2\nid: 'sky_start'\nrewards:\n  - item: 'minecraft:bread'\n    amount: 3\n"
        Files.writeString(path,original)
        StarterFoodUpgrade.migrate(root)
        assertEquals(original,Files.readString(path))
    }
}
