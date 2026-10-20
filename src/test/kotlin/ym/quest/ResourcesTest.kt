package ym.quest

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.configuration.ConfigurationSection
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.config.QuestFiles
import ym.quest.text.TextRenderer
import java.nio.file.Files
import java.nio.file.Path

class ResourcesTest {
    private val root=Path.of("src/main/resources")
    @Test fun `all resources parse have schemas RGB and complete menu mappings`() {
        Files.walk(root).use { paths -> paths.filter { it.toString().endsWith(".yml") }.forEach { p ->
            val raw=Files.readString(p)
            val yaml=YamlConfiguration().apply { loadFromString(raw) }
            assertFalse(raw.contains('\t'),p.toString())
            assertFalse(Regex("[&§][0-9a-fk-or]",RegexOption.IGNORE_CASE).containsMatchIn(raw),p.toString())
            if(p.fileName.toString() != "plugin.yml") assertEquals(if(p.parent.fileName.toString() in setOf("quests","chapters") || p in setOf(root.resolve("gui/main.yml"),root.resolve("提示.yml"))) 2 else 1,yaml.getInt("schema-version"),p.toString())
            if(p.parent.fileName.toString() == "gui") {
                QuestFiles.validateMenu(p.toString(),yaml)
                assertEquals(yaml.getStringList("Shape").joinToString("").filter { it != ' ' }.map { it.toString() }.toSet(),yaml.getConfigurationSection("components")!!.getKeys(false))
            }
        } }
        val lang=YamlConfiguration().apply { loadFromString(Files.readString(this@ResourcesTest.root.resolve("lang/zh_cn.yml"))) }
        QuestFiles.requiredMessages.forEach { assertTrue(lang.isString(it),it) }
    }
    @Test fun `actual shared renderer removes explicit nested italics and protects placeholders`() {
        val renderer=TextRenderer(YamlConfiguration())
        val result=renderer.item("<#123456><italic>根<value></italic>",mapOf("value" to Component.text("动态",).decorate(TextDecoration.ITALIC).append(Component.text("子").decorate(TextDecoration.ITALIC))))
        fun check(c: Component) { assertEquals(TextDecoration.State.FALSE,c.decoration(TextDecoration.ITALIC)); c.children().forEach(::check) }
        check(result)
        val translation=TextRenderer.noItalic(Component.translatable("example",Component.text("参数").decorate(TextDecoration.ITALIC))) as net.kyori.adventure.text.TranslatableComponent
        check(translation.arguments().single().value() as Component)
        val text=renderer.render("<value>",mapOf("value" to Component.text("<click:run_command:'/op me'>")))
        assertNull(text.clickEvent())
        val evidence=Path.of("target/verification/components.json"); Files.createDirectories(evidence.parent)
        val samples=JsonArray()
        val values=listOf("quest","status","page","pages","info","amount","description","objectives","rewards","reward","progress","target","chapter","mode","notifications","heading","name","next","value","objective","completed","total","hint","step").associateWith {
            renderer.render("<#7DD3FC>实际动态参数")
        }
        for(name in listOf("lang/zh_cn.yml","gui/main.yml","gui/chapters.yml","chapters/第一章·星海初醒.yml","quests/能源启动.yml","quests/星海启航.yml","quests/第一批资源.yml")) {
            val yaml=YamlConfiguration().apply { loadFromString(Files.readString(this@ResourcesTest.root.resolve(name))) }
            fun record(path: String, value: Any?) {
                when(value) {
                    is ConfigurationSection -> value.getValues(false).forEach { (k,v) -> record(if(path.isEmpty()) k else "$path.$k",v) }
                    is List<*> -> value.forEachIndexed { i,v -> record("$path.$i",v) }
                    is String -> {
                        val item=name.startsWith("gui/") && (path == "reward-format" || path.split('.').any { it in setOf("name","lore") })
                        if(name.startsWith("gui/") && !item && path != "title") return
                        val final=if(item) renderer.item(value,values) else renderer.render(value,values)
                        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
                        samples.add(JsonObject().apply {
                            addProperty("path","src/main/resources/$name"); addProperty("field",path)
                            addProperty("text_hash",hash);addProperty("runtime","Quest TextRenderer 1.0.0 / Adventure runtime")
                            addProperty("source","plugin_renderer_test")
                            add("placeholders",JsonArray().apply { values.keys.forEach { add(it) } })
                            add("component",JsonParser.parseString(GsonComponentSerializer.gson().serialize(final)))
                        })
                    }
                }
            }
            record("",yaml)
        }
        Files.writeString(evidence,samples.toString())
    }
}
