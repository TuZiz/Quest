package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.ResourceUpgrade
import ym.quest.config.SilverCoinCommentUpgrade
import ym.quest.preferences.*
import ym.quest.storage.Database
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.logging.Logger

class DefaultTrackingUpgradeTest {
    @TempDir lateinit var root: Path
    @Test fun `new player tracks automatically but saved off survives reopen`() {
        val id=UUID.randomUUID(); val file=root.resolve("quest.db")
        var db=Database(file,Logger.getAnonymousLogger());db.open().get(5,TimeUnit.SECONDS)
        try {
            val store=DisplayPreferenceStore(db)
            val fresh=store.load(id).get(5,TimeUnit.SECONDS)
            assertEquals(HudMode.ALWAYS,fresh.mode);assertTrue(fresh.autoTracking)
            store.save(id,fresh.copy(mode=HudMode.OFF,autoTracking=false)).get(5,TimeUnit.SECONDS)
        } finally { db.closeAsync().get(5,TimeUnit.SECONDS) }
        db=Database(file,Logger.getAnonymousLogger());db.open().get(5,TimeUnit.SECONDS)
        try {
            val saved=DisplayPreferenceStore(db).load(id).get(5,TimeUnit.SECONDS)
            assertEquals(HudMode.OFF,saved.mode);assertFalse(saved.autoTracking)
        } finally { db.closeAsync().get(5,TimeUnit.SECONDS) }
    }
    @Test fun `comment migration preserves reward values custom text and backups`() {
        val path=root.resolve("quests/支线.yml");Files.createDirectories(path.parent)
        val before="schema-version: 2\nid: 'test'\n# 自定义注释\nrewards:\n  - item: 'currency:silver_coin'  # 银币，每枚右键兑换10金币\n    amount: 3\ncustom: '银币，每枚右键兑换10金币'\n"
        Files.writeString(path,before)
        SilverCoinCommentUpgrade.migrate(root)
        val after=Files.readString(path)
        assertTrue(after.contains("# 银币，每枚右键兑换5金币"))
        assertEquals(YamlConfiguration().apply {loadFromString(before)}.getValues(true),YamlConfiguration().apply {loadFromString(after)}.getValues(true))
        SilverCoinCommentUpgrade.migrate(root);assertEquals(after,Files.readString(path))
        assertTrue(Files.exists(root.resolve(".migrations/silver-coin-comment-v1/complete")))
    }
    @Test fun `tip added to old language once while customization remains`() {
        val defaults=Files.readString(Path.of("src/main/resources/lang/zh_cn.yml"))
        val old=defaults.lines().filterNot {it.startsWith("task-tracking-tip:")}.joinToString("\n")
            .replace("✔ 已认识引航员！","✔ 自定义交谈完成！")
        val path=root.resolve("lang.yml");Files.writeString(path,old)
        ResourceUpgrade.appendLanguage(path,defaults)
        val once=Files.readString(path)
        val y=YamlConfiguration().apply {loadFromString(once)}
        assertTrue(y.getString("task-tracking-tip")!!.contains("右上角"))
        assertTrue(y.getString("task-tracking-tip")!!.contains("自动追踪"))
        assertTrue(y.getString("introduction.complete")!!.contains("自定义交谈完成"))
        ResourceUpgrade.appendLanguage(path,defaults);assertEquals(once,Files.readString(path))
    }
}
