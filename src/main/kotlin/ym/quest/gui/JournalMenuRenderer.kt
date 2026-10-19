package ym.quest.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.quest.config.Settings
import ym.quest.domain.*
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer

/** Paper ownership thread only; renders exactly the configured Shape, with no JDBC or file access. */
class JournalMenuRenderer(private val items: ItemFactory, private val text: TextRenderer) {
    private val lore = JournalLore(items,text)
    fun layout(settings: Settings, s: MenuSession) = if(s.chapter == null) settings.chapterMenu else settings.center
    fun render(player: Player, s: MenuSession, journal: JournalView, settings: Settings, productionReady: Boolean) {
        val chapter = journal.chapters.firstOrNull { it.definition.id == s.chapter }
        val views = chapter?.definition?.quests?.mapNotNull { id -> journal.quests.firstOrNull { it.definition.id == id } } ?: emptyList()
        val y = layout(settings,s)
        val shape = y.getStringList("Shape").joinToString("")
        val chapterList = s.chapter == null
        val capacity = shape.count { y.getString("components.$it.action") == if(chapterList) "chapter" else "quest" }
        val count = if(chapterList) journal.chapters.size else views.size
        val pages = if(capacity == 0) 1 else ((count+capacity-1)/capacity).coerceAtLeast(1)
        s.page = s.page.coerceIn(0,pages-1)
        val quests = views.drop(s.page*capacity).iterator()
        val chapters = journal.chapters.drop(s.page*capacity).iterator()
        s.menu.clear(); s.actions.clear()
        shape.forEachIndexed { slot,ch ->
            if(ch == ' ') return@forEachIndexed
            val section = checkNotNull(y.getConfigurationSection("components.$ch"))
            val action = section.getString("action","")!!
            val chView = if(action == "chapter") { if(chapters.hasNext()) chapters.next() else return@forEachIndexed } else chapter
            val q = if(action == "quest") { if(quests.hasNext()) quests.next() else return@forEachIndexed } else null
            val id = when {
                action in setOf("chapter","chapter-summary") -> chView?.definition?.icon ?: section.getString("item")!!
                q != null -> q.definition.icon
                else -> section.getString("item")!!
            }
            var unavailable=false
            val item = try { items.build(id,player) } catch(e: IllegalStateException) {
                unavailable=true; items.build(y.getString("unavailable-item")!!,player)
            }
            val values = mutableMapOf<String,Component>("page" to Component.text(s.page+1),"pages" to Component.text(pages))
            if(q != null) {
                values["quest"] = text.render(q.definition.name)
                values["status"] = text.format("status.${q.status}")
            } else if(chView != null) {
                values["chapter"] = text.render(chView.definition.name)
                values["status"] = text.format("chapter-status.${chView.status}")
                values["progress"] = Component.text(chView.completed)
                values["target"] = Component.text(chView.definition.quests.size)
            }
            val lines = lore.render(player,section.getStringList("lore"),values,q,chView,y.getString("reward-format"))
            if(unavailable || (q?.definition?.objectives?.any { it.type.startsWith("genlite.") } == true && !productionReady))
                lines += TextRenderer.noItalic(text.format("unavailable"))
            item.editMeta { meta ->
                section.getString("name")?.let { meta.displayName(text.item(it,values)) }
                meta.lore(lines)
            }
            s.menu.setItem(slot,item)
            s.actions[slot] = if(action == "chapter") "chapter:${chView!!.definition.id}" else action
        }
    }
}
