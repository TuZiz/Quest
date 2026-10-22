package ym.quest

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.TrackingHudTextUpgrade
import java.nio.file.Files
import java.nio.file.Path

class TrackingHudTextUpgradeTest {
    @TempDir lateinit var root: Path
    @Test fun `updates bundled wording with originals and custom fields retained and is idempotent`() {
        Files.createDirectories(root.resolve("gui"))
        val old=Files.readString(Path.of("src/main/resources/gui/tasks.yml"))
            .replace(Regex("点击[^'\\r\\n]*HUD"),"点击追踪进行中的任务；未解锁任务也可阅读。")+"\n# 保留注释\ncustom: '自定义'\n"
        Files.writeString(root.resolve("gui/tasks.yml"),old)
        TrackingHudTextUpgrade.migrate(root)
        val next=Files.readString(root.resolve("gui/tasks.yml"))
        assertTrue(next.contains("点击追踪并开启 HUD；"))
        assertTrue(next.endsWith("# 保留注释\ncustom: '自定义'\n"))
        assertEquals(old,Files.readString(root.resolve(".migrations/tracking-hud-v1/0.before")))
        TrackingHudTextUpgrade.migrate(root)
        assertEquals(next,Files.readString(root.resolve("gui/tasks.yml")))
    }
    @Test fun `custom wording is unchanged`() {
        Files.createDirectories(root.resolve("gui"))
        val custom=Files.readString(Path.of("src/main/resources/gui/settings.yml"))
            .replace("关闭时保留已选任务；追踪任务会自动开启。","自定义显示说明。")
        Files.writeString(root.resolve("gui/settings.yml"),custom)
        TrackingHudTextUpgrade.migrate(root)
        assertEquals(custom,Files.readString(root.resolve("gui/settings.yml")))
    }
    @Test fun `legacy settings lose automatic tracking button and preserve other components`() {
        Files.createDirectories(root.resolve("gui"))
        val old=Files.readString(Path.of("src/main/resources/gui/settings.yml"))
            .replace("  H N S  "," H A N S ")+"\n  A:\n    item: 'minecraft:compass'\n    action: 'toggle-auto'\n    name: '自动追踪'\n"
        Files.writeString(root.resolve("gui/settings.yml"),old)
        TrackingHudTextUpgrade.migrate(root)
        val raw=Files.readString(root.resolve("gui/settings.yml"))
        val next=org.bukkit.configuration.file.YamlConfiguration().apply { loadFromString(raw) }
        assertFalse(next.contains("components.A"))
        assertFalse(next.getStringList("Shape").any { 'A' in it })
        assertEquals("toggle-hud",next.getString("components.H.action"))
        assertEquals("toggle-notices",next.getString("components.N.action"))
    }
}
