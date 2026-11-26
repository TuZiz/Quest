package ym.quest.gui.journal

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.inventory.ItemStack
import ym.quest.domain.*

/** Paper thread only. Server-owned item models receive data; no textures or IO in Quest. */
internal object JournalCardVisuals {
    fun progress(quest: QuestView?, chapter: ChapterView?): Pair<Long, Long> {
        if (quest != null) {
            val target = quest.progress.sumOf { it.objective.target }
            return quest.progress.sumOf { it.progress.coerceIn(0, it.objective.target) } to target
        }
        return (chapter?.completed?.toLong() ?: 0) to (chapter?.definition?.requiredQuests?.size?.toLong() ?: 0)
    }
    fun apply(item: ItemStack, y: YamlConfiguration, quest: QuestView?, chapter: ChapterView?) {
        val id = quest?.definition?.id ?: chapter?.definition?.id ?: return
        val model = y.getString("cards.models.$id", y.getString("cards.fallback-model"))!!
        val (current, target) = progress(quest, chapter)
        val status = quest?.status?.name ?: chapter?.status?.name ?: "LOCKED"
        val percent = if (target > 0) (current.toDouble() / target * 100).coerceIn(0.0,100.0).toFloat() else 0f
        data(item, model, listOf(status, if(target>99) "PERCENT" else "COUNT"),
            listOf(if(target>99) percent else current.toFloat(), target.toFloat(), percent))
    }
    fun control(item: ItemStack, component: ConfigurationSection, s: JournalSession, enabled: Boolean) {
        val model = component.getString("visual-model") ?: return
        data(item, model, listOf(s.category.name, if(enabled) "ENABLED" else "DISABLED"), listOf(s.page+1f, s.pages.toFloat()))
    }
    private fun data(item: ItemStack, model: String, strings: List<String>, floats: List<Float>) {
        item.editMeta { meta ->
            meta.setItemModel(requireNotNull(NamespacedKey.fromString(model)) { "Invalid journal model $model" })
            val data = meta.customModelDataComponent
            data.strings = strings
            data.floats = floats
            meta.setCustomModelDataComponent(data)
        }
    }
}
