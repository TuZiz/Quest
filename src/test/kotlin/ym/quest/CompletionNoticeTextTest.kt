package ym.quest

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.ResourceUpgrade
import ym.quest.hud.CompletionNoticeText
import ym.quest.hud.HudNotice
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class CompletionNoticeTextTest {
    @TempDir lateinit var root: Path
    private val bundled=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
    private val language=YamlConfiguration().apply { loadFromString(bundled) }
    private fun plain(value: String) = PlainTextComponentSerializer.plainText().serialize(MiniMessage.miniMessage().deserialize(value))
    @Test fun `completion card has dynamic task name and does not assert reward arrival`() {
        val values=CompletionNoticeText.values(HudNotice("island","first_fuel","task-complete","✦ <#78FFD6>能源启动","下一任务"),TextRenderer(language),::plain)
        assertEquals("任务完成 · 能源启动",values["heading"])
        assertEquals("奖励自动发放",values["detail"])
        assertFalse(values.values.any { "已自动发放" in it || "<#" in it })
    }
    @Test fun `all four existing events keep independent language templates and constrained widths`() {
        for(event in listOf("task-start","task-complete","chapter-start","chapter-complete")) {
            val values=CompletionNoticeText.values(HudNotice("island","long",event,"超长中文任务名称".repeat(4)),TextRenderer(language),::plain)
            assertTrue(values.getValue("heading").endsWith("…"))
            assertFalse(values.getValue("detail").isBlank())
            assertEquals(values["heading"],ym.quest.hud.HudText.fit(values.getValue("heading"),27))
        }
    }
    @Test fun `custom next task placeholder remains configurable without a second toast`() {
        language.set("completion-hud.task-complete.detail","<#C9C6C0>下一任务：<next>")
        val values=CompletionNoticeText.values(HudNotice("i","fuel","task-complete","能源启动","✦ 新任务"),TextRenderer(language),::plain)
        assertEquals("下一任务：新任务",values["detail"])
    }
    @Test fun `new language block migration appends only once and preserves old text and comments`() {
        val file=root.resolve("zh_cn.yml")
        val old="# 保留自定义\nschema-version: 1\nunknown: '我的配置'\nhud-notices:\n  next: '自定义下一任务'\n"
        Files.writeString(file,old);ResourceUpgrade.appendLanguage(file,bundled)
        val next=Files.readString(file)
        assertTrue(next.startsWith(old.trimEnd()))
        val y=YamlConfiguration().apply { loadFromString(next) }
        assertEquals("自定义下一任务",y.getString("hud-notices.next"))
        assertTrue(y.isString("completion-hud.task-complete.heading"))
        assertTrue(y.isString("hud-test.help"))
        ResourceUpgrade.appendLanguage(file,bundled);assertEquals(next,Files.readString(file))
    }
}
