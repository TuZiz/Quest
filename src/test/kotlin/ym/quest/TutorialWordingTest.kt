package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.TutorialWordingUpgrade
import ym.quest.domain.*
import ym.quest.hud.HudProjection
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class TutorialWordingTest {
    @TempDir lateinit var root: Path
    private fun load(value: String)=YamlConfiguration().apply { loadFromString(value) }
    private fun resource(path: String)=Files.readString(Path.of("src/main/resources",ym.quest.config.QuestTaskFiles.bundled(path)))

    @Test fun `default wording migration keeps amounts rewards custom fields comments and backups`() {
        val paths=listOf("quests/第一章/第一批资源.yml","quests/第一章/收获的喜悦.yml","quests/第一章/机械革新.yml",
            "quests/第一章/星海启航.yml","quests/第一章/支线/自由的生产.yml","chapters/第一章·星海初醒.yml")
        val originals=paths.associateWith { path ->
            val before=resource(path).replace("圆石","原石").replace("引航员 NPC","领航员 NPC")
                .replace("打开生成器面板<newline>切换一次 ","在生成器面板中切换一次 ")
                .replace("升至<newline>","升至 ")
                .replace("target: 32","target: 64").replace(">32</",">64</")+"\n# 中文注释\ncustom: '保留'\n"
            val file=root.resolve(path);Files.createDirectories(file.parent);Files.writeString(file,before)
            before
        }
        val custom=root.resolve("quests/custom.yml")
        val customized=resource(paths.first()).replace("圆石生成器累计生产<newline>","自定义原石教学：")
        Files.writeString(custom,customized)
        TutorialWordingUpgrade.migrate(root)
        val outputs=paths.associateWith { path ->
            val after=Files.readString(root.resolve(path));val before=load(originals.getValue(path));val next=load(after)
            assertEquals(before.getMapList("rewards"),next.getMapList("rewards"))
            assertEquals(before.getMapList("tasks"),next.getMapList("tasks"))
            assertEquals(before.getMapList("objectives").map { it.filterKeys { key -> key!="description" } },next.getMapList("objectives").map { it.filterKeys { key -> key!="description" } })
            assertTrue(after.contains("# 中文注释\ncustom: '保留'"))
            val visible=(next.getStringList("description")+next.getStringList("story")+next.getMapList("objectives").map { it["description"].toString() }).joinToString()
            assertFalse(visible.contains("原石"),path);assertFalse(visible.contains("领航员"),path)
            after
        }
        assertTrue(outputs.getValue(paths.first()).contains(">64</"))
        assertTrue(outputs.getValue(paths[4]).contains("打开生成器面板<newline>切换一次"))
        assertEquals(customized,Files.readString(custom))
        val backups=Files.walk(root.resolve(".migrations/tutorial-wording-v2")).use { stream ->
            stream.filter { it.fileName.toString().endsWith(".before") }.map { Files.readString(it) }.toList()
        }
        assertTrue(originals.values.all { it in backups })
        TutorialWordingUpgrade.migrate(root)
        outputs.forEach { (path,after) -> assertEquals(after,Files.readString(root.resolve(path))) }
    }
    @Test fun `already migrated server gets explicit level line in TAB and HUD without changing rewards`() {
        val path="quests/第一章/机械革新.yml"
        val before=resource(path).replace("升至<newline>","升至 ")
        val file=root.resolve(path);Files.createDirectories(file.parent);Files.writeString(file,before)
        Files.createDirectories(root.resolve(".migrations/tutorial-wording-v1"))
        Files.writeString(root.resolve(".migrations/tutorial-wording-v1/complete"),"schema-version: 2\n")
        TutorialWordingUpgrade.migrate(root)
        val y=load(Files.readString(file));val row=y.getMapList("objectives").single()
        assertEquals(load(before).getMapList("rewards"),y.getMapList("rewards"))
        val goal=Objective("experience","genlite.level",2,"cobblestone_generator",row["description"].toString())
        val q=QuestDefinition("upgrade_two",y.getString("name")!!,emptyList(),"minecraft:anvil",emptyList(),listOf(goal),emptyList())
        val journal=JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(goal,1)))),emptyList())
        val text=TextRenderer(load(resource("lang/zh_cn.yml")))
        val hud=HudProjection(text,26).projectGrouped(journal)!!
        assertTrue(hud.getValue("objective-2").contains("Lv.2"))
        val plain=net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
        val mini=net.kyori.adventure.text.minimessage.MiniMessage.miniMessage()
        val tab=ym.quest.tab.TabQuestProjection(text,28).project(journal).map { plain.serialize(mini.deserialize(it)) }
        val index=tab.indexOfFirst { it.contains("目标：") }
        assertTrue(index>=0)
        assertTrue(tab[index].contains("将圆石生成器升至"))
        assertEquals("  Lv.2",tab[index+1])
        TutorialWordingUpgrade.migrate(root)
        assertEquals(y.getMapList("objectives"),load(Files.readString(file)).getMapList("objectives"))
    }
    @Test fun `production mode objective stays complete in two HUD rows`() {
        val y=load(resource("quests/第一章/支线/自由的生产.yml"))
        val raw=y.getMapList("objectives").single()
        val goal=Objective("experience","genlite.mode",1,null,raw["description"].toString())
        val q=QuestDefinition("free_production",y.getString("name")!!,emptyList(),"minecraft:comparator",emptyList(),listOf(goal),emptyList(),category=QuestCategory.SIDE)
        val p=HudProjection(TextRenderer(load(resource("lang/zh_cn.yml"))),26)
        val result=p.projectGrouped(JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(goal,0)))),emptyList()))!!
        assertTrue(result.getValue("side-objective-1").contains("打开生成器面板"))
        assertTrue(result.getValue("side-objective-2").contains("切换一次"))
        assertTrue(result.getValue("side-objective-2").contains("产出模式"))
        assertFalse(result.values.any { it.contains("..") })
        assertTrue(resource("lang/zh_cn.yml").substringAfter("introduction:").contains("引航员"))
        assertTrue(resource("quests/第一章/星海启航.yml").contains("引航员 NPC"))
    }
}
