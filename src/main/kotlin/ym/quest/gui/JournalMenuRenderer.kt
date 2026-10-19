package ym.quest.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.quest.config.Settings
import ym.quest.domain.*
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer

/** Paper ownership thread only; rendering has no JDBC or file access. */
class JournalMenuRenderer(private val items: ItemFactory, private val text: TextRenderer) {
    fun layout(settings: Settings, s: MenuSession) = when {
        s.quest != null -> settings.detail
        s.chapters && s.chapter != null -> settings.chapterDetail
        s.chapters -> settings.chapterMenu
        else -> settings.center
    }
    fun render(player: Player, s: MenuSession, journal: JournalView, settings: Settings, productionReady: Boolean) {
        val all = journal.quests
        val chapter = journal.chapters.firstOrNull { it.definition.id == s.chapter }
        val views = if(s.chapters && chapter != null) chapter.definition.quests.mapNotNull { id -> all.firstOrNull { it.definition.id == id } } else all
        val y = layout(settings,s)
        val shape = y.getStringList("Shape").joinToString("")
        val current = views.firstOrNull { it.definition.id == s.quest }
        val chapterList = s.chapters && s.quest == null && s.chapter == null
        val chapterDetail = s.chapters && s.quest == null && chapter != null
        val capacity = shape.count { ch -> y.getString("components.$ch.action") == if(chapterList) "chapter" else "quest" }
        val count = if(chapterList) journal.chapters.size else views.size
        val rewardCapacity = shape.count { y.getString("components.$it.action") == "chapter-preview" }
        val taskPages = if(capacity == 0) 1 else ((count+capacity-1)/capacity).coerceAtLeast(1)
        val rewardPages = if(rewardCapacity == 0) 1 else (((chapter?.definition?.rewards?.size ?: 0)+rewardCapacity-1)/rewardCapacity).coerceAtLeast(1)
        val pages = maxOf(taskPages,rewardPages)
        s.page = s.page.coerceIn(0,pages-1)
        val list = if(s.quest == null) views.drop(s.page*capacity).iterator() else emptyList<QuestView>().iterator()
        val chapterIterator = journal.chapters.drop(s.page*capacity).iterator()
        val rewardList = (if(chapterDetail) chapter.definition.rewards else current?.definition?.rewards)?.drop(if(chapterDetail) s.page*shape.count { y.getString("components.$it.action") == "chapter-preview" } else 0)?.iterator()
        s.menu.clear(); s.actions.clear()
        shape.forEachIndexed { slot,ch ->
            if(ch == ' ') return@forEachIndexed
            val section = checkNotNull(y.getConfigurationSection("components.$ch"))
            val action = section.getString("action","")!!
            val chView = if(action == "chapter") { if(chapterIterator.hasNext()) chapterIterator.next() else return@forEachIndexed } else chapter
            val q = if(action == "quest") { if(list.hasNext()) list.next() else return@forEachIndexed } else current
            val reward = if(action in setOf("preview","chapter-preview")) { if(rewardList?.hasNext() == true) rewardList.next() else return@forEachIndexed } else null
            val id = when { reward != null -> reward.item; action in setOf("chapter","chapter-summary") -> chView?.definition?.icon ?: section.getString("item")!!; action in setOf("quest","summary") -> q?.definition?.icon ?: section.getString("item")!!; else -> section.getString("item")!! }
            var unavailable=false
            val item = try { items.build(id,player) } catch(e: IllegalStateException) {
                unavailable=true; items.build(y.getString("unavailable-item")!!,player)
            }
            val values = mutableMapOf<String,Component>("page" to Component.text(s.page+1),"pages" to Component.text(pages))
            if(q != null) {
                values["quest"] = text.render(q.definition.name)
                values["status"] = text.format("status.${q.status}")
            }
            if(chView != null && q == null) {
                values["chapter"] = text.render(chView.definition.name)
                values["status"] = text.format("chapter-status.${chView.status}")
                values["progress"] = Component.text(chView.completed)
                values["target"] = Component.text(chView.definition.quests.size)
            }
            val lore = section.getStringList("lore").map { text.item(it,values) }.toMutableList()
            if(unavailable || (q?.definition?.objectives?.any { it.type.startsWith("genlite.") } == true && !productionReady)) {
                lore += TextRenderer.noItalic(text.format("unavailable"))
            }
            if(q != null && action in setOf("quest","summary","objectives")) {
                if(action != "objectives") lore += q.definition.description.map { text.item(it) }
                lore += q.progress.map { p -> TextRenderer.noItalic(text.format("objective-line",mapOf(
                    "description" to text.render(p.objective.description),"progress" to Component.text(p.progress),"target" to Component.text(p.objective.target)))) }
            }
            if(chView != null && action in setOf("chapter","chapter-summary","chapter-claim")) {
                lore += chView.definition.description.map { text.item(it) }
                lore += TextRenderer.noItalic(text.format("chapter-reward-status",mapOf("status" to text.format(
                    "status.${if(chView.claimed) "CLAIMED" else if(chView.status == ChapterStatus.COMPLETE) "COMPLETE" else "LOCKED"}"))))
            }
            if(reward != null) {
                item.amount = reward.amount.coerceAtMost(item.maxStackSize)
                lore += TextRenderer.noItalic(text.format("reward-line",TextRenderer.values("amount" to reward.amount)))
            }
            item.editMeta { meta ->
                section.getString("name")?.let { meta.displayName(text.item(it,values)) }
                meta.lore(lore)
            }
            s.menu.setItem(slot,item)
            s.actions[slot] = when(action) { "quest" -> "quest:${q!!.definition.id}"; "chapter" -> "chapter:${chView!!.definition.id}"; else -> action }
        }
    }
}
