package ym.quest.hud

import org.bukkit.configuration.file.YamlConfiguration

data class HudSettings(val hideCompleted: Boolean, val bar: Boolean, val merge: Boolean,
                       val duration: Int, val capacity: Int, val settle: Int, val slide: Int, val textUnits: Int) {
    companion object {
        fun parse(y: YamlConfiguration): HudSettings {
            fun bool(key: String, default: Boolean): Boolean {
                require(!y.contains(key) || y.isBoolean(key)) { "显示.yml:$key=${y.get(key)} expected boolean" }
                return y.getBoolean(key,default)
            }
            fun int(key: String, default: Int, range: IntRange): Int {
                require(!y.contains(key) || y.isInt(key)) { "显示.yml:$key=${y.get(key)} expected integer" }
                return y.getInt(key,default).also { require(it in range) { "显示.yml:$key=$it expected $range" } }
            }
            return HudSettings(bool("hide-completed",false),bool("progress-bar",true),bool("merge-notifications",true),
                int("notification-ticks",80,40..200),int("notification-limit",4,1..8),int("notification-merge-ticks",6,0..20),
                int("notification-slide-ticks",8,0..16),int("text-half-em-limit",20,16..40))
        }
    }
}
