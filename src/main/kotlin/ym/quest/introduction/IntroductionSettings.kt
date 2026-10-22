package ym.quest.introduction

import org.bukkit.configuration.file.YamlConfiguration

data class IntroductionSettings(val enabled: Boolean = false, val npcId: String = "新人",
    val chapterId: String = "sky_awakening", val taskId: String = "meet_guide", val timeoutSeconds: Int = 120) {
    companion object {
        fun parse(y: YamlConfiguration): IntroductionSettings {
            val file = "引导.yml"
            require(y.get("schema-version") == 1) { "$file:schema-version=${y.get("schema-version")} expected 1" }
            require(y.isBoolean("enabled")) { "$file:enabled=${y.get("enabled")} expected boolean" }
            fun id(key: String): String = y.getString(key)?.takeIf { it.isNotBlank() }
                ?: error("$file:$key=${y.get(key)} expected nonblank ID")
            val npc = id("npc-id"); val chapter = id("chapter-id"); val task = id("task-id")
            require(chapter.matches(Regex("[a-z0-9_-]+")) && task.matches(Regex("[a-z0-9_-]+"))) { "$file: invalid chapter-id=$chapter/task-id=$task" }
            require(y.get("timeout-seconds") is Int && y.getInt("timeout-seconds") in 15..600) { "$file:timeout-seconds=${y.get("timeout-seconds")} expected 15..600" }
            return IntroductionSettings(y.getBoolean("enabled"),npc,chapter,task,y.getInt("timeout-seconds"))
        }
    }
}
