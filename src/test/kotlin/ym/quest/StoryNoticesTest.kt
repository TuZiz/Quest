package ym.quest

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.config.ChapterFiles
import ym.quest.config.NoticeFiles
import ym.quest.domain.ProgressChange
import ym.quest.domain.QuestDefinition
import ym.quest.text.StoryNotices
import ym.quest.text.TextRenderer
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class StoryNoticesTest {
    private fun yaml(name: String)=YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/$name"))) }
    @Test fun `task and chapter completion send dynamic chat without resource pack or HUD calls`() {
        val calls=mutableListOf<String>();val messages=mutableListOf<String>()
        val plain=PlainTextComponentSerializer.plainText()
        val player=Proxy.newProxyInstance(Player::class.java.classLoader,arrayOf(Player::class.java)) { _,method,args ->
            calls+=method.name
            if(method.name == "sendMessage") args?.filterIsInstance<Component>()?.forEach { messages+=plain.serialize(it) }
            null
        } as Player
        val chapter=ChapterFiles.parse(yaml("chapters/第一章·星海初醒.yml"))
        val quest=QuestDefinition("first_fuel","<#94E88B>能源启动",emptyList(),"minecraft:coal",emptyList(),emptyList(),emptyList())
        val notices=StoryNotices(TextRenderer(yaml("lang/zh_cn.yml")),NoticeFiles.parse(yaml("提示.yml")))
        for(change in listOf(ProgressChange("home",quest.id,true),ProgressChange("home",chapter.id,true,chapter=true))) {
            calls.clear();messages.clear()
            notices.send(player,change,listOf(quest),listOf(chapter))
            assertEquals(listOf("sendMessage"),calls)
            assertEquals(1,messages.size);assertFalse(messages.single().contains("<#"))
            val expected = if(change.chapter) plain.serialize(TextRenderer(yaml("lang/zh_cn.yml")).render(chapter.name)) else "能源启动"
            assertTrue(messages.single().contains(expected))
        }
        calls.clear()
        notices.send(player,ProgressChange("home",quest.id,false),listOf(quest),listOf(chapter))
        assertTrue(calls.isEmpty())
    }
}
