package ym.quest.gui.journal

import org.bukkit.configuration.file.YamlConfiguration
import ym.quest.domain.ChapterView
import ym.quest.domain.JournalView
import ym.quest.domain.QuestCategory

/** Display-only future covers. These never become chapter definitions or player progress. */
data class ChapterCardPreview(val order: Int, val name: String, val icon: String,
    val model: String, val lore: List<String>)

data class ChapterCardEntry(val chapter: ChapterView?, val preview: ChapterCardPreview?) {
    val order get() = chapter?.definition?.order ?: requireNotNull(preview).order
}

object ChapterCardPreviews {
    private const val path = "cards.chapter-previews"
    private val resourceId = Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")

    fun validate(y: YamlConfiguration) { read(y) }

    fun entries(y: YamlConfiguration, view: JournalView, session: JournalSession): List<ChapterCardEntry> {
        val all = y.getString("cards.chapter-preview-category")=="ALL"
        val real = (if(all) view.chapters else JournalNavigation.chapters(view,session)).map { ChapterCardEntry(it,null) }
        val previews = read(y)
        if (previews.isEmpty()) return real
        if (!all && session.category.name != y.getString("cards.chapter-preview-category")) return real
        val occupied = view.chapters.map { it.definition.order }.toSet()
        return (real + previews.filter { it.order !in occupied }.map { ChapterCardEntry(null,it) }).sortedBy { it.order }
    }

    private fun read(y: YamlConfiguration): List<ChapterCardPreview> {
        if (!y.contains(path)) return emptyList()
        require(y.isConfigurationSection(path)) { "$path expected a mapping" }
        require(JournalCardLayout.parse(y) != null) { "$path requires the six-card layout" }
        require(y.getStringList("Shape").joinToString("").any { y.getString("components.$it.action")=="journal-chapter" }) {
            "$path is only supported on the chapter list"
        }
        val category = y.getString("cards.chapter-preview-category")
        require(category=="ALL" || QuestCategory.entries.any { it.name==category }) { "cards.chapter-preview-category=$category invalid category" }
        val rawLore = y.getList("cards.chapter-preview-lore")
        require(rawLore != null && rawLore.all { it is String }) { "cards.chapter-preview-lore expected text lines" }
        val lore = y.getStringList("cards.chapter-preview-lore")
        val section = y.getConfigurationSection(path)!!
        val result = section.getKeys(false).map { key ->
            val base = "$path.$key"
            val p = section.getConfigurationSection(key) ?: error("$base expected a mapping")
            require(p.isInt("order") && p.getInt("order")>0) { "$base.order=${p.get("order")} expected a positive integer" }
            val name = p.getString("name")
            require(!name.isNullOrBlank()) { "$base.name expected nonempty text" }
            val icon = p.getString("item")
            val model = p.getString("visual-model")
            require(icon != null && resourceId.matches(icon)) { "$base.item=$icon invalid resource ID" }
            require(model != null && resourceId.matches(model)) { "$base.visual-model=$model invalid resource ID" }
            ChapterCardPreview(p.getInt("order"),name,icon,model,lore)
        }
        require(result.map { it.order }.distinct().size==result.size) { "$path contains duplicate order values" }
        return result
    }
}
