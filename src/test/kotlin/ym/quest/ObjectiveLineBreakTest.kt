package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import ym.quest.config.ObjectiveLineBreakUpgrade
import ym.quest.domain.*
import ym.quest.hud.HudProjection
import ym.quest.tab.TabQuestProjection
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class ObjectiveLineBreakTest {
    @TempDir lateinit var root: Path
    private val names=listOf("第一批资源","收获的喜悦","自动化初体验","资源的归宿")
    private fun resource(name: String)=Files.readString(Path.of("src/main/resources",ym.quest.config.QuestTaskFiles.bundled("quests/第一章/$name.yml")))
    private fun yaml(raw: String)=YamlConfiguration().apply { loadFromString(raw) }

    @Test fun `quantity and item stay together on second TAB and HUD objective rows`() {
        val text=TextRenderer(yaml(Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))))
        val mini=MiniMessage.miniMessage()
        val plain=PlainTextComponentSerializer.plainText()
        names.forEach { name ->
            val y=yaml(resource(name))
            val raw=y.getMapList("objectives").single()
            val description=raw["description"].toString()
            val quantity=plain.serialize(mini.deserialize(description.substringAfter("<newline>")))
            val objective=Objective(raw["id"].toString(),raw["type"].toString(),(raw["target"] as Number).toLong(),null,description)
            val q=QuestDefinition(y.getString("id")!!,y.getString("name")!!,emptyList(),y.getString("icon")!!,emptyList(),listOf(objective),emptyList())
            val view=JournalView(listOf(QuestView(q,QuestStatus.ACTIVE,listOf(ObjectiveProgress(objective,0)))),emptyList())
            val tab=TabQuestProjection(text,28).project(view).map { plain.serialize(mini.deserialize(it)) }
            val index=tab.indexOfFirst { it.contains("目标：") }
            assertTrue(index>=0)
            assertEquals("  $quantity",tab[index+1],name)
            assertFalse(tab[index].contains(objective.target.toString()),name)
            val hud=HudProjection(text,28).projectGrouped(view)!!
            assertTrue(hud.getValue("objective-2").contains(objective.target.toString()),name)
            assertTrue(hud.getValue("objective-2").contains(quantity.substringAfter(' ')),name)
            assertFalse(hud.getValue("objective-1").replace(Regex("#[0-9a-fA-F]{6}"),"").contains(objective.target.toString()),name)
        }
    }

    @Test fun `migration preserves configurable amounts rewards comments and custom wording with original backups`() {
        val files=names.map { name ->
            val file=root.resolve("quests/第一章/$name.yml")
            Files.createDirectories(file.parent)
            val before=resource(name).replace("<newline>"," ").replace("向收集器自动传输 ","生成器向收集器自动传输 ")+"\n# 用户注释\ncustom: '保留'\n"
            Files.writeString(file,before)
            file to before
        }
        val custom=root.resolve("quests/custom.yml")
        val customized=resource("第一批资源").replace("圆石生成器累计生产<newline>","自定义说明：").replace("target: 32","target: 64")
        Files.writeString(custom,customized)
        ObjectiveLineBreakUpgrade.migrate(root)
        files.forEach { (file,before) ->
            val after=Files.readString(file)
            val old=yaml(before);val next=yaml(after)
            assertTrue(after.contains("<newline>"))
            assertTrue(after.contains("# 用户注释\ncustom: '保留'"))
            assertEquals(old.getMapList("rewards"),next.getMapList("rewards"))
            assertEquals(old.getMapList("objectives").single()["target"],next.getMapList("objectives").single()["target"])
            ObjectiveLineBreakUpgrade.migrate(root)
            assertEquals(after,Files.readString(file))
        }
        assertEquals(customized,Files.readString(custom))
        val backups=Files.walk(root.resolve(".migrations/objective-quantity-lines-v1")).use { paths ->
            paths.filter { it.fileName.toString().endsWith(".before") }.map { Files.readString(it) }.toList()
        }
        assertTrue(files.all { it.second in backups })
    }
    @Test fun `island entry hint migration retains custom text target reward and original backup`() {
        val raw=resource("星海启航")
        val description=yaml(raw).getMapList("objectives").single()["description"].toString()
        val original=raw.replace(description,description.substringBefore("<newline>"))+"\n# 自定义注释\ncustom: '保留'\n"
        val file=root.resolve("quests/第一章/星海启航.yml")
        Files.createDirectories(file.parent);Files.writeString(file,original)
        val custom=file.resolveSibling("custom.yml")
        val customText=original.replace("id: 'sky_start'","id: 'custom'").replace("创建或加入","自定义入岛")
        Files.writeString(custom,customText)
        ym.quest.config.IslandEntryHintUpgrade.migrate(root)
        val updated=Files.readString(file)
        assertTrue(updated.contains("领航员 NPC"));assertTrue(updated.contains("服务器菜单"))
        assertTrue(updated.contains("# 自定义注释"))
        assertEquals(yaml(original).getMapList("rewards"),yaml(updated).getMapList("rewards"))
        assertEquals(1,yaml(updated).getMapList("objectives").single()["target"])
        assertEquals(customText,Files.readString(custom))
        assertEquals(original,Files.readString(root.resolve(".migrations/island-entry-hint-v1/0.before")))
        ym.quest.config.IslandEntryHintUpgrade.migrate(root)
        assertEquals(updated,Files.readString(file))
    }

}
