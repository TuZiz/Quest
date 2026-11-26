package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.LogisticsTutorialUpgrade
import ym.quest.domain.ConditionRegistry
import ym.quest.domain.Objective
import java.nio.file.*
import org.junit.jupiter.api.Assertions.*

class LogisticsTutorialUpgradeTest {
    @TempDir lateinit var root: Path
    private fun resource(name: String)=checkNotNull(javaClass.getResourceAsStream("/"+ym.quest.config.QuestTaskFiles.bundled(name))).bufferedReader().use {it.readText()}
    @Test fun migratesRequirementsPreservingIdsRewardsCommentsUnknownsAndIsIdempotent() {
        val file=root.resolve("quests/第一章/机械的联系.yml");Files.createDirectories(file.parent)
        Files.writeString(file,"schema-version: 2\nid: 'first_binding'\n# 自定义注释\nname: '旧任务'\nunknown: 73\nobjectives:\n  - id: 'experience'\n    type: 'genlite.bind'\n    target: 1\nrewards:\n  - item: 'currency:copper_coin'\n    amount: 99\n")
        val chapter=root.resolve("chapters/第一章.yml");Files.createDirectories(chapter.parent)
        Files.writeString(chapter,resource("chapters/第一章·星海初醒.yml").replace("\r\n","\n").replace(Regex("(?m)^  - quest: 'logistics_supply'\\n    requires:\\n      - 'first_extract'\\n      - 'stone_site_materials'\\n    optional: true\\n"),""))
        LogisticsTutorialUpgrade.migrate(root,::resource)
        val raw=Files.readString(file);val y=YamlConfiguration().apply {loadFromString(raw)}
        assertEquals("first_binding",y.getString("id"));assertEquals(73,y.getInt("unknown"))
        assertEquals(99,y.getMapList("rewards").single()["amount"]);assertTrue(raw.contains("# 自定义注释"))
        assertEquals("genlite.connected",y.getMapList("objectives").single()["type"])
        assertTrue(Files.exists(root.resolve(LogisticsTutorialUpgrade.supplyResource)))
        assertEquals(1,YamlConfiguration().apply {load(chapter.toFile())}.getMapList("tasks").count {it["quest"]=="logistics_supply"})
        LogisticsTutorialUpgrade.migrate(root,::resource);assertEquals(raw,Files.readString(file))
        assertTrue(Files.exists(root.resolve(".migrations/logistics-core-v1/complete")))
    }
    @Test fun newConditionsHaveCorrectStateAndEventSemantics() {
        val registry=ConditionRegistry();assertTrue(registry.contains("genlite.connected"));assertTrue(registry.contains("genlite.energy"))
        assertTrue(Objective("connect","genlite.connected",1,null,"接入").state)
        assertFalse(Objective("supply","genlite.energy",1,null,"供能").state)
    }
    @Test fun completedAutomaticRolloutStillReceivesManualBindingTutorialWithoutRewardReset() {
        val marker=root.resolve(".migrations/logistics-core-v1/complete")
        Files.createDirectories(marker.parent);Files.writeString(marker,"schema-version: 2\n")
        val file=root.resolve("quests/第一章/机械的联系.yml");Files.createDirectories(file.parent)
        Files.writeString(file,resource("quests/第一章/机械的联系.yml").replace("手持绑定工具，分别右键生成器和物流核心。","自动接入范围内的生成器。")+"custom: 81\n")
        LogisticsTutorialUpgrade.migrate(root,::resource)
        val once=Files.readString(file);assertTrue(once.contains("手持绑定工具"));assertFalse(once.contains("自动接入范围内"))
        val y=YamlConfiguration().apply {loadFromString(once)}
        assertEquals(81,y.getInt("custom"));assertEquals("first_binding",y.getString("id"))
        assertEquals("genlite.connected",y.getMapList("objectives").single()["type"])
        assertEquals("currency:copper_coin",y.getMapList("rewards").single()["item"])
        LogisticsTutorialUpgrade.migrate(root,::resource);assertEquals(once,Files.readString(file))
        assertTrue(Files.exists(root.resolve(".migrations/manual-logistics-binding-v1/complete")))
    }
}
