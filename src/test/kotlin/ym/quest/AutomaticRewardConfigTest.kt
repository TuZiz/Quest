package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.RewardSettings
import ym.quest.config.RewardTextUpgrade
import java.nio.file.Files
import java.nio.file.Path

class AutomaticRewardConfigTest {
    @TempDir lateinit var root: Path
    @Test fun `legacy reward instructions migrate once while unknown fields and comments survive`() {
        val file=root.resolve("语言.yml")
        val original="# 中文注释\nschema-version: 1\ncustom: '保留自定义内容'\ncomplete: '<#6EE7B7>任务完成：<quest><#CBD5E1>，使用 /quest 领取奖励。'\n"
        Files.writeString(file,original)
        RewardTextUpgrade.migrate(file)
        val migrated=Files.readString(file)
        assertEquals(original.replace("，使用 /quest 领取奖励。","，奖励会自动发放。"),migrated)
        RewardTextUpgrade.migrate(file)
        assertEquals(migrated,Files.readString(file))
    }
    @Test fun `automatic defaults and retry limits are explicit and validated`() {
        val y=YamlConfiguration().apply { loadFromString(Files.readString(Path.of("src/main/resources/奖励.yml"))) }
        assertEquals(RewardSettings(true,100),RewardSettings.parse(y))
        y.set("retry-ticks",0)
        assertThrows(IllegalArgumentException::class.java) { RewardSettings.parse(y) }
        y.set("retry-ticks",100);y.set("automatic","true")
        assertThrows(IllegalArgumentException::class.java) { RewardSettings.parse(y) }
    }
}
