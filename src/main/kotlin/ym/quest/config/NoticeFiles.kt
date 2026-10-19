package ym.quest.config

import org.bukkit.configuration.file.YamlConfiguration

data class NoticeProfile(val chat: Boolean, val actionBar: Boolean, val title: Boolean,
    val sound: String?, val volume: Float, val pitch: Float)

object NoticeFiles {
    val events = listOf("chapter-start","task-start","task-complete","next-task","chapter-complete")
    fun parse(y: YamlConfiguration): Map<String,NoticeProfile> = events.associateWith { event ->
        val s = y.getConfigurationSection(event) ?: error("提示.yml:$event missing")
        for(key in listOf("chat","action-bar","title","sound-enabled"))
            require(s.get(key) is Boolean) { "提示.yml:$event.$key=${s.get(key)} expected boolean" }
        val sound = s.getString("sound")
        require(sound?.matches(Regex("[a-z0-9_.-]+:[a-z0-9_/.-]+")) == true) { "提示.yml:$event.sound=$sound invalid" }
        val volume = s.getDouble("volume",Double.NaN); val pitch = s.getDouble("pitch",Double.NaN)
        require(volume.isFinite() && volume in 0.0..4.0 && pitch.isFinite() && pitch in 0.5..2.0) { "提示.yml:$event volume=$volume pitch=$pitch invalid" }
        NoticeProfile(s.getBoolean("chat"),s.getBoolean("action-bar"),s.getBoolean("title"),
            sound.takeIf { s.getBoolean("sound-enabled") },volume.toFloat(),pitch.toFloat())
    }
}
