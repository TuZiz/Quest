package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration

data class RewardSettings(val automatic: Boolean, val retryTicks: Int) {
    companion object {
        fun parse(y: YamlConfiguration): RewardSettings {
            require(y.isBoolean("automatic")) { "奖励.yml:automatic=${y.get("automatic")} expected boolean" }
            require(y.isInt("retry-ticks") && y.getInt("retry-ticks") in 20..1200) {
                "奖励.yml:retry-ticks=${y.get("retry-ticks")} expected 20..1200"
            }
            return RewardSettings(y.getBoolean("automatic"),y.getInt("retry-ticks"))
        }
    }
}
