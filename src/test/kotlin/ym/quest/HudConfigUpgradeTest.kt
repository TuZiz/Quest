package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import ym.quest.config.HudConfigUpgrade
import org.bukkit.configuration.file.YamlConfiguration
import ym.quest.hud.HudSettings

class HudConfigUpgradeTest {
    @TempDir lateinit var root: Path
    private val bundled=Files.readString(Path.of("src/main/resources/显示.yml"))
    @Test fun `upgrade preserves comments switches unknown fields and durable original`() {
        val old="# 自定义保留\nschema-version: 1\nenabled: false\ntracker: true\nnotifications: false\nrefresh-ticks: 80\ntracker-layout: custom\npopup-prefix: old_\ncustom: 42\n"
        val path=root.resolve("显示.yml"); Files.writeString(path,old)
        HudConfigUpgrade.migrate(root,bundled)
        val result=Files.readString(path); val y=YamlConfiguration().apply { loadFromString(result) }
        assertEquals(4,y.getInt("schema-version")); assertFalse(y.getBoolean("enabled"))
        assertEquals("custom",y.getString("tracker-layout")); assertEquals(42,y.getInt("custom"));assertTrue(result.contains("# 自定义保留"))
        assertEquals(120,HudSettings.parse(y).expandTicks)
        assertEquals(old,Files.readString(root.resolve(".migrations/hud-v4/0.before")))
        HudConfigUpgrade.migrate(root,bundled);assertEquals(result,Files.readString(path))
    }
    @Test fun `invalid or future config never overwritten`() {
        val path=root.resolve("显示.yml");val old="schema-version: 5\ncustom: 1\n";Files.writeString(path,old)
        assertThrows(IllegalArgumentException::class.java) { HudConfigUpgrade.migrate(root,bundled) }
        assertEquals(old,Files.readString(path))
        assertThrows(IllegalArgumentException::class.java) { HudSettings.parse(YamlConfiguration().apply { loadFromString("notification-limit: 999") }) }
    }
    @Test fun `schema three upgrades append only sidebar options and preserve custom switches`() {
        val old="# 保留关闭设置与未知字段\nschema-version: 3\ntracker: false\nnotifications: false\ncustom: '我的布局'\n"
        val file=root.resolve("显示.yml");Files.writeString(file,old)
        HudConfigUpgrade.migrate(root,bundled)
        val result=Files.readString(file)
        val y=YamlConfiguration().apply { loadFromString(result) }
        assertEquals(4,y.getInt("schema-version"));assertFalse(y.getBoolean("tracker"));assertFalse(y.getBoolean("notifications"))
        assertEquals("我的布局",y.getString("custom"));assertTrue(result.contains("# 保留关闭设置与未知字段"))
        assertEquals("quest_sidebar",HudSettings.parse(y).sidebarLayout);assertTrue(HudSettings.parse(y).sidebarBackground)
        assertEquals(old,Files.readString(root.resolve(".migrations/hud-v4/0.before")))
        HudConfigUpgrade.migrate(root,bundled);assertEquals(result,Files.readString(file))
    }
}
