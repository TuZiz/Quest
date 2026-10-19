package ym.quest.hud

import org.bukkit.configuration.file.YamlConfiguration

data class HudSettings(val bar: Boolean, val merge: Boolean, val duration: Int, val capacity: Int,
                       val settle: Int, val slide: Int, val textUnits: Int, val compactUnits: Int,
                       val expandedLayout: String, val compactLayout: String, val expandTicks: Int,
                       val cooldownTicks: Int, val milestones: List<Int>) {
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
            fun layout(key: String, default: String) = y.getString(key,default)!!.also {
                require(it.isNotBlank()) { "显示.yml:$key=$it expected layout ID" }
            }
            val milestones=if(y.contains("expand-milestones")) {
                val raw=y.getList("expand-milestones") ?: error("显示.yml:expand-milestones expected list")
                require(raw.all { it is Int && it in 1..99 } && raw.distinct().size == raw.size) { "显示.yml:expand-milestones=$raw expected unique integers 1..99" }
                raw.map { it as Int }.sorted()
            } else listOf(10,25,50,75,90)
            return HudSettings(bool("progress-bar",true),bool("merge-notifications",true),
                int("notification-ticks",60,40..200),int("notification-limit",4,1..8),int("notification-merge-ticks",6,0..20),
                int("notification-slide-ticks",8,0..16),int("text-half-em-limit",20,16..40),int("compact-name-half-em-limit",14,8..24),
                layout("tracker-layout","quest_tracker"),layout("compact-layout","quest_tracker_compact"),
                int("expand-ticks",120,100..160),int("expand-cooldown-ticks",200,180..1200),milestones)
        }
    }
}
