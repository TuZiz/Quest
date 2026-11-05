package ym.quest

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.domain.*
import ym.quest.gui.JournalLore
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class JournalLoreTest {
    private val plain=PlainTextComponentSerializer.plainText()
    private val text=TextRenderer(YamlConfiguration().apply {
        loadFromString(Files.readString(Path.of("src/main/resources/lang/zh_cn.yml")))
    })
    private val lore=JournalLore(ItemFactory(emptyList()),text)
    private val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,_ ->
        error("Unexpected Bukkit call during text rendering: ${method.name}")
    } as Player
    private fun task(description: List<String>)=QuestView(QuestDefinition("intro","引航员",description,"minecraft:book",
        emptyList(),emptyList(),emptyList()),QuestStatus.COMPLETE,emptyList())
    private fun render(template: List<String>,description: List<String>)=
        lore.render(player,template,emptyMap(),task(description),null,null)
    private fun checkStyle(c: Component) {
        assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC))
        c.children().forEach(::checkStyle)
    }

    @Test fun `decorated descriptions expand each line and preserve literals and non italics`() {
        val result=render(listOf("  <#94A3B8><description>"),listOf("第一行","<#89F7FE><italic>第二行<newline>第三行</italic>"))
        assertEquals(listOf("  第一行","  第二行","  第三行"),result.map(plain::serialize))
        result.forEach(::checkStyle)
        val dynamic=lore.render(player,listOf("<#94A3B8><description>"),mapOf("name" to Component.text("<description>")),
            task(listOf("<#94A3B8><name>")),null,null)
        assertEquals("<description>",plain.serialize(dynamic.single()))
        assertNull(dynamic.single().clickEvent())
    }

    @Test fun `empty lists omit markers and undecorated legacy markers still work`() {
        assertEquals(listOf("前","后"),render(listOf("前","  <#94A3B8><description>","<objectives>","<rewards>","后"),emptyList()).map(plain::serialize))
        assertEquals(listOf("旧版说明"),render(listOf("<description>"),listOf("旧版说明")).map(plain::serialize))
    }

    @Test fun `bundled chapter and task templates display actual descriptions`() {
        val values=listOf("quest","chapter","status","tracking","progress","target","percent","page","pages","info").associateWith { text.render("<#89F7FE>示例") }
        val chapter=ChapterView(ChapterDefinition("one","第一章","minecraft:book",listOf("<#94A3B8>章节说明"),0,emptyList(),emptyList(),emptyList()),ChapterStatus.ACTIVE,0,false)
        for((file,key) in listOf("tasks" to "Q","chapters" to "C")) {
            val y=YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/gui/$file.yml"))) }
            val template=y.getStringList("components.$key.lore")
            assertTrue(template.any { "<description>" in it },"$file must use real description template")
            val rendered=lore.render(player,template,values,if(file=="tasks") task(listOf("<#94A3B8>与引航员交谈")) else null,chapter,y.getString("reward-format"))
            val output=rendered.joinToString("\n",transform=plain::serialize)
            assertTrue(output.contains(if(file=="tasks") "与引航员交谈" else "章节说明"),output)
            assertFalse(output.contains("<description>"),output)
            rendered.forEach(::checkStyle)
        }
    }
}
