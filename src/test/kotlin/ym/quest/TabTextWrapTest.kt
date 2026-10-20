package ym.quest

import net.kyori.adventure.text.minimessage.MiniMessage
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.tab.TabTextWrap

class TabTextWrapTest {
    @Test fun `wrap includes labels retains colors and does not duplicate numbering`() {
        val source = MiniMessage.miniMessage().deserialize("<#94A3B8>目标  <#FDE68A><bold>添加燃料添加燃料</bold><newline>2. 点击「燃料」")
        val lines = TabTextWrap(16).lines(source)
        val plain = PlainTextComponentSerializer.plainText()
        assertEquals("目标  添加燃料添加燃料2. 点击「燃料」", lines.joinToString("") { plain.serialize(it) })
        assertTrue(lines.all { plain.serialize(it).codePoints().map { p -> if(p > 255) 2 else 1 }.sum() <= 16 })
        val gson = GsonComponentSerializer.gson()
        assertTrue(gson.serialize(lines[1]).contains("#FDE68A", ignoreCase=true))
        assertTrue(gson.serialize(lines[1]).contains("\"bold\":true"))
    }
}
