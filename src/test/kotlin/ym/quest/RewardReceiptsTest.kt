package ym.quest

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.ResourceUpgrade
import ym.quest.domain.Parcel
import ym.quest.reward.RewardReceipts
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class RewardReceiptsTest {
    @TempDir lateinit var root: Path
    private val bundled = Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
    private val text = TextRenderer(YamlConfiguration().apply { loadFromString(bundled) })
    private fun parcel(id: String,item: String,amount: Int,task: String="fuel") =
        Parcel(id,"home",task,item,amount,"DELIVERED","player")
    private fun plain(value: Component) = PlainTextComponentSerializer.plainText().serialize(value)

    @Test fun `receipt combines acknowledged item quantities once and isolates tasks`() {
        val receipts=RewardReceipts(text)
        val first=parcel("1","genlite:mechanical_parts",2)
        receipts.record(first,Component.text("铁片"))
        receipts.record(first,Component.text("铁片"))
        receipts.record(parcel("2","genlite:mechanical_parts",3),Component.text("铁片"))
        receipts.record(parcel("3","genlite:copper_coil",2),Component.text("铜丝"))
        receipts.record(parcel("4","currency:copper_coin",10,"chapter:first"),Component.text("铜币"))
        assertEquals("铁片 ×5、铜丝 ×2",plain(receipts.take("fuel")!!))
        assertNull(receipts.take("fuel"))
        assertEquals(listOf("chapter:first"),receipts.tasks())
        assertEquals("铜币 ×10",plain(receipts.take("chapter:first")!!))
    }
    @Test fun `partial receipt lists only confirmed entries and item components remain non italic`() {
        val receipts=RewardReceipts(text)
        assertNull(receipts.take("fuel"))
        receipts.record(parcel("1","genlite:mechanical_parts",2),
            Component.text("铁").append(Component.text("片").decorate(TextDecoration.ITALIC)))
        val rewards=receipts.take("fuel")!!
        assertEquals("铁片 ×2",plain(rewards))
        fun check(c: Component) {
            assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC))
            c.children().forEach(::check)
        }
        check(rewards)
        val message=text.format("reward-delivered",mapOf("quest" to Component.text("能源启动"),"rewards" to rewards))
        assertTrue(plain(message).contains("能源启动 奖励已到账：铁片 ×2"))
    }
    @Test fun `language upgrade adds receipt formatting without replacing custom text or comments`() {
        val file=root.resolve("zh_cn.yml")
        Files.writeString(file,"schema-version: 1\n# 保留提示\nreward-delivered: '自定义发放提示'\n")
        ResourceUpgrade.appendLanguage(file,bundled)
        val once=Files.readString(file)
        ResourceUpgrade.appendLanguage(file,bundled)
        assertEquals(once,Files.readString(file))
        val y=YamlConfiguration().apply { loadFromString(once) }
        assertEquals("自定义发放提示",y.getString("reward-delivered"))
        assertTrue(once.contains("# 保留提示"))
        assertTrue(y.isString("reward-delivered-item"))
        assertTrue(y.isString("reward-delivered-separator"))
    }
}
