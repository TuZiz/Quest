package ym.quest

import org.bukkit.configuration.file.YamlConfiguration
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import ym.quest.config.MenuConfigUpgrade
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties

class MenuConfigUpgradeTest {
    @TempDir lateinit var root: Path
    private val defaults get()=listOf("chapters","tasks","settings").associateWith { Files.readString(Path.of("src/main/resources/gui/$it.yml")) }
    private fun put(path: String,raw: String) { val file=root.resolve(path);Files.createDirectories(file.parent);Files.writeString(file,raw) }
    private fun read(path: String)=Files.readString(root.resolve(path))
    private fun menuNames()=Files.list(root.resolve("gui")).use { paths -> paths.map { it.fileName.toString() }.toList().toSet() }
    private fun migrate()=MenuConfigUpgrade.migrate(root,defaults)
    private fun yaml(raw: String)=YamlConfiguration().apply { loadFromString(raw) }
    private fun oldInstall(): Map<String,String> {
        val home="# 保留当前章节自定义注释\n"+defaults.getValue("chapters").replace("schema-version: 2","schema-version: 1")+"\ncustom-home: '保留主页未知字段'\n"
        val tasks="# 保留任务列表自定义注释\n"+defaults.getValue("tasks").replace(Regex("点击[^'\\r\\n]*HUD[^'\\r\\n]*"),"点击查看；未解锁任务也可阅读。")+"\ncustom-tasks: '保留任务未知字段'\n"
        return mapOf("gui/home.yml" to home,"gui/chapters.yml" to defaults.getValue("chapters").replace("schema-version: 2","schema-version: 1"),
            "gui/journal-chapters.yml" to defaults.getValue("chapters").replace("schema-version: 2","schema-version: 1"),
            "gui/journal-tasks.yml" to tasks,"gui/main.yml" to tasks,"gui/task.yml" to tasks,"gui/settings.yml" to defaults.getValue("settings"))
            .also { files -> files.forEach { (path,raw) -> put(path,raw) } }
    }
    @Test fun `fresh install exports exactly three menus and repeat migration is byte stable`() {
        migrate();assertEquals(setOf("chapters.yml","tasks.yml","settings.yml"),menuNames())
        val first=defaults.keys.associateWith { read("gui/$it.yml") };migrate()
        first.forEach { (name,raw) -> assertEquals(raw,read("gui/$name.yml")) }
    }
    @Test fun `seven files collapse to current three with comments unknown fields and full originals retained`() {
        val original=oldInstall();migrate()
        assertEquals(setOf("chapters.yml","tasks.yml","settings.yml"),menuNames())
        assertEquals(2,yaml(read("gui/chapters.yml")).getInt("schema-version"))
        assertEquals("保留主页未知字段",yaml(read("gui/chapters.yml")).getString("custom-home"))
        assertTrue(read("gui/chapters.yml").contains("# 保留当前章节自定义注释"))
        assertTrue(read("gui/tasks.yml").contains("# 保留任务列表自定义注释"))
        assertEquals("保留任务未知字段",yaml(read("gui/tasks.yml")).getString("custom-tasks"))
        assertTrue(read("gui/tasks.yml").contains("点击追踪进行中的任务"))
        val journal=root.resolve(".migrations/menus-v3")
        val manifest=Properties().apply { Files.newInputStream(journal.resolve("plan.properties")).use { load(it) } }
        for(index in 0 until manifest.getProperty("count").toInt()) {
            val path=manifest.getProperty("$index.path")
            original[path]?.let { assertEquals(it,Files.readString(journal.resolve("$index.before")),path) }
        }
        val next=defaults.keys.associateWith { read("gui/$it.yml") };migrate()
        next.forEach { (name,raw) -> assertEquals(raw,read("gui/$name.yml")) }
    }
    @Test fun `interrupted publication resumes and removes obsolete menus without losing edits`() {
        val original=oldInstall();migrate()
        Files.delete(root.resolve(".migrations/menus-v3/complete"))
        put("gui/home.yml",original.getValue("gui/home.yml"))
        migrate();assertFalse(Files.exists(root.resolve("gui/home.yml")))
        assertEquals(3,menuNames().size)
    }
    @Test fun `recovery refuses concurrent target edits before retiring restored source`() {
        val original=oldInstall();migrate();Files.delete(root.resolve(".migrations/menus-v3/complete"))
        put("gui/home.yml",original.getValue("gui/home.yml"));put("gui/tasks.yml",read("gui/tasks.yml")+"# 并发编辑\n")
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertTrue(read("gui/tasks.yml").endsWith("# 并发编辑\n"))
        assertEquals(original.getValue("gui/home.yml"),read("gui/home.yml"))
    }
    @Test fun `future schemas and invalid selected actions fail before modifying any menu`() {
        val original=oldInstall();put("gui/home.yml",original.getValue("gui/home.yml").replace("schema-version: 1","schema-version: 99"))
        assertThrows(IllegalArgumentException::class.java) { migrate() };assertEquals(7,menuNames().size)
        put("gui/home.yml",original.getValue("gui/home.yml").replace("action: 'settings'","action: 'unsupported-action'"))
        assertThrows(IllegalArgumentException::class.java) { migrate() };assertEquals(7,menuNames().size)
        assertFalse(Files.exists(root.resolve(".migrations/menus-v3/plan.properties")))
    }
    @Test fun `new customized targets win and obsolete menus cannot reappear after completion`() {
        val original=oldInstall()
        put("gui/chapters.yml",defaults.getValue("chapters")+"\ncustom: '新章节配置'\n")
        put("gui/tasks.yml",defaults.getValue("tasks")+"\ncustom: '新任务配置'\n")
        migrate();assertEquals("新章节配置",yaml(read("gui/chapters.yml")).getString("custom"))
        assertEquals("新任务配置",yaml(read("gui/tasks.yml")).getString("custom"))
        put("gui/main.yml",original.getValue("gui/main.yml"))
        assertThrows(IllegalArgumentException::class.java) { migrate() }
        assertEquals(original.getValue("gui/main.yml"),read("gui/main.yml"))
    }
}
