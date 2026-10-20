package ym.quest.tab

import org.bukkit.configuration.file.YamlConfiguration

data class TabSettings(val enabled: Boolean, val refreshTicks: Long, val lineWidth: Int) {
    companion object {
        fun parse(y: YamlConfiguration): TabSettings {
            require(y.get("enabled") is Boolean) { "tab.yml:enabled=${y.get("enabled")} expected boolean" }
            val ticks = y.get("refresh-ticks")
            val width = y.get("line-width")
            require(ticks is Int && ticks in 20..1200) { "tab.yml:refresh-ticks=$ticks expected integer 20..1200" }
            require(width is Int && width in 16..48) { "tab.yml:line-width=$width expected integer 16..48" }
            return TabSettings(y.getBoolean("enabled"), ticks.toLong(), width)
        }
    }
}
