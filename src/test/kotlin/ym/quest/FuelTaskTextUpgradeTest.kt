package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.FuelTaskTextUpgrade
import java.nio.file.Files
import java.nio.file.Path

class FuelTaskTextUpgradeTest {
    @TempDir lateinit var root: Path
    private fun yaml(raw: String)=YamlConfiguration().apply { loadFromString(raw) }
    @Test fun `existing fuel instructions migrate without resetting targets rewards or custom fields`() {
        val current=Files.readString(Path.of("src/main/resources/quests/第一章/能源启动.yml"))
        // Exercise the actual historical wording even when today's defaults change style.
        val old=current.replace(Regex("(?s)description:\\n.*?\\nobjectives:"),
            "description:\n  - '<#E2E8F0>点击<#FDE68A><bold>「燃料」</bold><#E2E8F0><newline>   放入燃料即可'\n\nobjectives:")
            .replace("target: 1","target: 3")+"\n# 自定义注释\ncustom: '保留'\n"
        val file=root.resolve("quests/能源启动.yml");Files.createDirectories(file.parent);Files.writeString(file,old)
        FuelTaskTextUpgrade.migrate(root)
        val once=Files.readString(file);val parsed=yaml(once)
        assertEquals(3,parsed.getMapList("objectives").single()["target"])
        assertEquals(yaml(old).getMapList("rewards"),parsed.getMapList("rewards"))
        assertEquals("genlite.fuel",parsed.getMapList("objectives").single()["type"])
        assertTrue(parsed.getStringList("description").any { it.contains("打开燃料仓") })
        assertTrue(parsed.getStringList("description").any { it.contains("Shift点击背包燃料") })
        assertTrue(once.contains("# 自定义注释"));assertEquals("保留",parsed.getString("custom"))
        FuelTaskTextUpgrade.migrate(root);assertEquals(once,Files.readString(file))
    }
    @Test fun `custom fuel descriptions and future schema are preserved`() {
        val file=root.resolve("quests/能源启动.yml");Files.createDirectories(file.parent)
        val custom="schema-version: 2\nid: first_fuel\ndescription:\n  - '<#123456>我的操作说明'\n"
        Files.writeString(file,custom);FuelTaskTextUpgrade.migrate(root);assertEquals(custom,Files.readString(file))
        val future=custom.replace("schema-version: 2","schema-version: 9")
        Files.writeString(file,future)
        assertThrows(IllegalArgumentException::class.java) { FuelTaskTextUpgrade.migrate(root) }
        assertEquals(future,Files.readString(file))
    }
}
