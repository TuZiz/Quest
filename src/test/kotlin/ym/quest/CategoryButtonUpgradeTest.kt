package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.CategoryButtonUpgrade
import java.nio.file.Files
import java.nio.file.Path

class CategoryButtonUpgradeTest {
    @TempDir lateinit var root: Path
    private val bundled get()=Files.readString(Path.of("src/main/resources/gui/chapters.yml")).replace("\r\n","\n")
    private fun yaml(raw: String)=YamlConfiguration().apply { loadFromString(raw) }
    @Test fun `old three controls become one centered control preserving chapter slots and custom fields`() {
        Files.createDirectories(root.resolve("gui"))
        val old=bundled.replace("####S###I","##M#S#X#I").replace("action: 'category-cycle'","action: 'category-side'")+
            "\n  M:\n    item: 'minecraft:compass'\n    action: 'category-main'\n  X:\n    item: 'minecraft:amethyst_shard'\n    action: 'category-special'\n"
        val customized=old.replace("  S:\n","  S:\n    custom: '保留字段'\n")+"\n# 保留根注释\nunknown: '保留'\n"
        val file=root.resolve("gui/chapters.yml");Files.writeString(file,customized)
        CategoryButtonUpgrade.migrate(root,bundled)
        val result=Files.readString(file);val y=yaml(result)
        assertFalse(y.contains("components.M"));assertFalse(y.contains("components.X"))
        assertEquals("category-cycle",y.getString("components.S.action"))
        assertEquals("保留字段",y.getString("components.S.custom"))
        assertEquals("保留",y.getString("unknown"))
        assertEquals("####S###I",y.getStringList("Shape").first())
        assertEquals(yaml(customized).getStringList("Shape").drop(1),y.getStringList("Shape").drop(1))
        assertEquals(customized,Files.readString(root.resolve(".migrations/category-button-v1/0.before")))
        CategoryButtonUpgrade.migrate(root,bundled);assertEquals(result,Files.readString(file))
    }
    @Test fun `current single button remains byte identical`() {
        Files.createDirectories(root.resolve("gui"));val file=root.resolve("gui/chapters.yml")
        Files.writeString(file,bundled);CategoryButtonUpgrade.migrate(root,bundled)
        assertEquals(bundled,Files.readString(file))
    }
}
