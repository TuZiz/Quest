package ym.quest

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.ResourceUpgrade
import ym.quest.platform.DisplayPreferenceCommands
import ym.quest.preferences.DisplayPreferences
import ym.quest.preferences.HudMode
import ym.quest.text.TextRenderer
import java.lang.reflect.Proxy
import java.nio.file.Files
import java.nio.file.Path

class DisplayPreferenceCommandsTest {
    @TempDir lateinit var root: Path
    private val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
    private val text=TextRenderer(YamlConfiguration().apply { loadFromString(bundled) })
    private val messages=mutableListOf<String>()
    private var permitted=true
    private var value: DisplayPreferences?=DisplayPreferences(trackedQuest="task")
    private var writes=0
    private var save=true
    private fun sender(player: Boolean=true): CommandSender {
        val type=if(player) Player::class.java else CommandSender::class.java
        return Proxy.newProxyInstance(type.classLoader,arrayOf(type)) { _,method,args ->
            when(method.name) {
                "hasPermission" -> permitted
                "sendMessage" -> { args?.filterIsInstance<Component>()?.forEach {
                    messages+=PlainTextComponentSerializer.plainText().serialize(it)
                };null }
                else -> null
            }
        } as CommandSender
    }
    private val commands=DisplayPreferenceCommands(text,{ value },{ _,change,done ->
        writes++
        if(save) value=change(value!!)
        done(save)
    })
    @Test fun `tracking commands persist desired state and retain unrelated preferences`() {
        val player=sender()
        for(setting in listOf("hud")) {
            val original=value!!
            fun state()=when(setting) {
                "hud" -> value!!.mode==HudMode.ALWAYS
                "notice" -> value!!.notifications
                else -> value!!.sound
            }
            for(operation in listOf("off","on","on","toggle")) {
                assertTrue(commands.handle(player,arrayOf(setting,operation)))
                assertEquals(operation=="on",state())
                assertEquals("task",value!!.trackedQuest)
                if(setting=="hud") assertEquals(state(),value!!.autoTracking)
                if(setting!="notice") assertEquals(original.notifications,value!!.notifications)
                if(setting!="sound") assertEquals(original.sound,value!!.sound)
                assertFalse(messages.last().contains("<state>"))
            }
        }
        assertEquals(4,writes)
    }
    @Test fun `invalid input denied permission console and loading never save`() {
        val player=sender()
        assertFalse(commands.handle(player,arrayOf("unknown","on")))
        assertFalse(commands.handle(player,arrayOf("notice","off")))
        assertFalse(commands.handle(player,arrayOf("sound","off")))
        for(args in listOf(arrayOf("hud"),arrayOf("hud","bad"),arrayOf("hud","on","extra")))
            assertTrue(commands.handle(player,args))
        assertTrue(messages.last().contains("用法"))
        permitted=false
        commands.handle(player,arrayOf("hud","on"))
        assertTrue(messages.last().contains("权限"))
        assertTrue(commands.complete(player,arrayOf("h")).isEmpty())
        permitted=true
        commands.handle(sender(false),arrayOf("hud","on"))
        assertTrue(messages.last().contains("仅限玩家"))
        value=null
        commands.handle(player,arrayOf("hud","on"))
        assertTrue(messages.last().contains("稍后重试"))
        assertEquals(0,writes)
    }
    @Test fun `failed storage retains settings and reports failure with completions`() {
        val before=value
        save=false
        commands.handle(sender(),arrayOf("hud","on"))
        assertEquals(before,value)
        assertTrue(messages.last().contains("未能完成"))
        assertEquals(listOf("hud"),commands.complete(sender(),arrayOf("h")))
        assertEquals(listOf("on","off"),commands.complete(sender(),arrayOf("hud","o")))
        assertTrue(commands.complete(sender(),arrayOf("guide","")).isEmpty())
    }
    @Test fun `existing language appends command messages without altering custom text or comments`() {
        val old=YamlConfiguration().apply { loadFromString(bundled) }
        old.set("preference-command",null)
        old.set("command-help.player","custom help")
        val file=root.resolve("zh_cn.yml")
        Files.writeString(file,"# preserve comment\n"+old.saveToString())
        val original=Files.readString(file)
        ResourceUpgrade.appendLanguage(file,bundled)
        val migrated=Files.readString(file)
        assertTrue(migrated.startsWith(original.trimEnd()))
        val loaded=YamlConfiguration().apply { loadFromString(migrated) }
        assertEquals("custom help",loaded.getString("command-help.player"))
        assertNotNull(loaded.getString("preference-command.saved"))
        ResourceUpgrade.appendLanguage(file,bundled)
        assertEquals(migrated,Files.readString(file))
    }
}
