package ym.quest.hud

import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.Path

/** Behaviour only. Layouts, fonts and textures belong exclusively to the server's Xerr folder. */
data class HudSettings(val enabled: Boolean = false, val tracker: String = "quest_tracker",
    val notice: String = "quest_complete", val refreshTicks: Int = 20,
    val taskTicks: Int = 72, val chapterTicks: Int = 112, val fadeTicks: Int = 6,
    val queueLimit: Int = 4, val textWidth: Int = 28, val templates: Map<String,String> = emptyMap(),
    val colorMap: Map<String,String> = emptyMap()) {
    companion object {
        fun load(file: Path): HudSettings {
            if(!Files.exists(file)) return HudSettings()
            val y=YamlConfiguration().apply { loadFromString(Files.readString(file)) }
            fun number(key: String, range: IntRange): Int {
                val value=y.get(key)
                require(value is Int && value in range) { "$file:$key=$value expected $range; schema=1" }
                return value
            }
            fun id(key: String): String {
                val value=y.getString(key)
                require(value != null && value.matches(Regex("[a-z0-9_-]+"))) { "$file:$key=$value invalid layout ID; schema=1" }
                return value
            }
            require(y.get("schema-version") == 1) { "$file:schema-version=${y.get("schema-version")} expected 1" }
            require(y.get("enabled") is Boolean) { "$file:enabled=${y.get("enabled")} expected boolean; schema=1" }
            val section=y.getConfigurationSection("text") ?: error("$file:text missing mapping; schema=1")
            listOf("chapter","task","objective","heading","name","detail","progress-text").forEach { key ->
                require(section.isString(key)) { "$file:text.$key missing template; schema=1" }
            }
            val templates=section.getKeys(false).associateWith { key ->
                val value=y.getString("text.$key")
                require(value != null && value.contains("{value}") && !value.contains("<#") && !value.contains("<gradient:")) { "$file:text.$key=$value expected TabooLib Components template with {value}; schema=1" }
                value
            }
            require(!y.contains("color-map") || y.isConfigurationSection("color-map")) { "$file:color-map expected mapping; schema=1" }
            val colors=y.getConfigurationSection("color-map")?.getValues(false)?.entries?.associate { (from,to) ->
                require(from.matches(Regex("#[0-9a-fA-F]{6}")) && to is String && to.matches(Regex("#[0-9a-fA-F]{6}"))) { "$file:color-map.$from=$to expected RGB hex pair; schema=1" }
                from.uppercase() to to.uppercase()
            } ?: emptyMap()
            return HudSettings(y.getBoolean("enabled"),id("tracker-layout"),id("completion-layout"),
                number("refresh-ticks",20..200),number("task-duration-ticks",40..400),
                number("chapter-duration-ticks",40..400),number("fade-ticks",1..20),
                number("queue-limit",1..8),number("text-width",20..40),templates,colors)
        }
    }
}
