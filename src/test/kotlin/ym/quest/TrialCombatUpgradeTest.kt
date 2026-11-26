package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.TrialCombatUpgrade
import java.nio.file.Files
import java.nio.file.Path

class TrialCombatUpgradeTest {
    @TempDir lateinit var root: Path
    @Test fun `trial upgrade preserves IDs rewards comments unknown fields and is idempotent`() {
        val original="""
            schema-version: 2
            id: 'first_trial'
            name: '<gradient:#78FFD6:#A8FF78>✦ 初次试炼</gradient>'
            description:
              - '<#94A3B8>星海之外，并不只有宁静的家园。'
              - '<#94A3B8>探索未知之前，请先握紧手中的剑。'
            objectives:
              - id: 'experience'
                type: 'combat.kill'
                resource: 'minecraft:zombie'
                target: 3
                description: '<#E2E8F0>亲自击杀 <#78FFD6>3</#78FFD6> 只僵尸'
            rewards:
              - item: 'genlite:fuel'
                amount: 8
            # 自定义注释
            custom: '保留'
        """.trimIndent()+"\n"
        val file=root.resolve("quests/初次试炼.yml")
        Files.createDirectories(file.parent);Files.writeString(file,original)
        TrialCombatUpgrade.migrate(root)
        val after=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(after) }
        assertEquals("first_trial",y.getString("id"))
        assertEquals("hostile",y.getMapList("objectives").single()["resource"])
        assertEquals("experience",y.getMapList("objectives").single()["id"])
        assertEquals(3,y.getMapList("objectives").single()["target"])
        assertEquals(YamlConfiguration().apply { loadFromString(original) }.getMapList("rewards"),y.getMapList("rewards"))
        assertTrue(after.contains("# 自定义注释\ncustom: '保留'"))
        assertTrue(after.contains("前往石址击杀三只生物"))
        assertEquals(original,Files.readString(root.resolve(".migrations/trial-hostile-v1/0.before")))
        TrialCombatUpgrade.migrate(root)
        assertEquals(after,Files.readString(file))
    }
}
