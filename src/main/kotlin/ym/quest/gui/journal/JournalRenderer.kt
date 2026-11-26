package ym.quest.gui.journal

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import ym.quest.config.Settings
import ym.quest.domain.*
import ym.quest.gui.JournalLore
import ym.quest.preferences.*
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer

/** Configured chest screens, Paper-owned. No IO, progress mutation or reward actions. */
class JournalRenderer(private val items: ItemFactory,private val text: TextRenderer) {
    private val lore=JournalLore(items,text)
    private val previewItems=ChapterPreviewItems(items,text)
    fun render(player: Player,s: JournalSession,view: JournalView,prefs: DisplayPreferences,settings: Settings,pageOnly: Boolean=false) {
        val y=settings.journalMenus.getValue(s.screen.file)
        val shape=y.getStringList("Shape").joinToString("")
        val tracked=TrackingSelection.select(view,prefs)
        val chapter=view.chapters.firstOrNull { it.definition.id==s.chapter }
        val chapterList=s.screen==JournalPage.CHAPTERS
        val chapters=if(chapterList) ChapterCardPreviews.entries(y,view,s) else emptyList()
        val tasks=JournalNavigation.tasks(view,s)
        val repeating=if(chapterList) "journal-chapter" else "journal-quest"
        val cards=JournalCardLayout.parse(y)
        val capacity=cards?.capacity ?: shape.count { y.getString("components.$it.action")==repeating }
        val count=if(chapterList) chapters.size else tasks.size
        val pages=if(capacity==0) 1 else ((count+capacity-1)/capacity).coerceAtLeast(1)
        s.pages=pages
        s.page=s.page.coerceIn(0,pages-1)
        val chapterIterator=chapters.drop(s.page*capacity).iterator()
        val taskIterator=tasks.drop(s.page*capacity).iterator()
        val pageChapters=chapters.drop(s.page*capacity).take(capacity)
        val pageTasks=tasks.drop(s.page*capacity).take(capacity)
        val contents=if(pageOnly) s.menu.contents.copyOf() else arrayOfNulls<ItemStack>(s.menu.size)
        val actions=if(pageOnly) s.actions.toMutableMap() else mutableMapOf<Int,String>()
        val changedSlots=mutableSetOf<Int>()
        shape.forEachIndexed { slot,symbol ->
            if(symbol==' ') return@forEachIndexed
            val component=y.getConfigurationSection("components.$symbol")!!
            if(pageOnly && !JournalPageSlots.dependsOnPage(component)) return@forEachIndexed
            changedSlots+=slot
            contents[slot]=null;actions.remove(slot)
            if(component.getBoolean("pack-only") && !player.hasResourcePack()) return@forEachIndexed
            var action=component.getString("action","")!!
            val q=when(action) {
                "journal-quest" -> if(cards!=null) pageTasks.getOrNull(cards.entry(slot)!!) ?: return@forEachIndexed
                    else if(taskIterator.hasNext()) taskIterator.next() else return@forEachIndexed
                else -> null
            }
            val chapterEntry=if(action=="journal-chapter") {
                if(cards!=null) pageChapters.getOrNull(cards.entry(slot)!!) ?: return@forEachIndexed
                else if(chapterIterator.hasNext()) chapterIterator.next() else return@forEachIndexed
            } else null
            chapterEntry?.preview?.let { preview ->
                contents[slot]=previewItems.build(player,preview,y,cards!!.anchor(slot))
                return@forEachIndexed
            }
            val ch=chapterEntry?.chapter ?: chapter
            val values=mutableMapOf<String,Component>("page" to Component.text(s.page+1),"pages" to Component.text(pages),
                "category" to text.format("journal.category-${s.category}"),
                "info" to text.format(if(count==0) "journal.empty" else "journal.browse"),
                "quest" to (q?.let { text.render(it.definition.name).append(text.format("task-scope.suffix",mapOf("scope" to text.format("task-scope.${it.definition.scope}")))) } ?: text.format("journal.no-selection")),
                "chapter" to (ch?.let { text.render(it.definition.name) } ?: text.format("journal.no-selection")),
                "status" to (q?.let { text.format("status.${it.status}") } ?: ch?.let { text.format("chapter-status.${it.status}") } ?: text.format("journal.empty")),
                "tracking" to text.format(if(q!=null && q.definition.id==tracked?.definition?.id) "journal.tracked" else "journal.not-tracked"),
                "progress" to Component.text(ch?.completed ?: 0),"target" to Component.text(ch?.definition?.requiredQuests?.size ?: 0),
                "percent" to Component.text(if(ch==null) 0 else ch.completed*100/ch.definition.requiredQuests.size.coerceAtLeast(1)),
                "hud-mode" to text.format("journal.mode-${prefs.mode}"),
                "auto" to enabled(prefs.autoTracking),"notifications" to enabled(prefs.notifications),"sound" to enabled(prefs.sound))
            val state=when(action) {
                "toggle-hud" -> prefs.mode!=HudMode.OFF
                else -> null
            }
            val configured=if(player.hasResourcePack()) {
                (if(state!=null) component.getString(if(state) "item-on" else "item-off") else null)
                    ?: component.getString("pack-item")
            } else null
            val card=cards?.entry(slot)!=null
            val packed=player.hasResourcePack()
            val id=if(card && (!cards!!.anchor(slot) || packed)) {
                if(packed) y.getString(if(cards.anchor(slot)) "cards.base-item" else "cards.blank-item")!! else component.getString("item")!!
            } else q?.definition?.icon ?: if(action=="journal-chapter") ch!!.definition.icon
                else if(packed && component.isString("visual-model")) component.getString("item")!!
                else configured ?: component.getString("item")!!
            var unavailable=false
            val item=try { items.build(id,player) } catch(e: IllegalStateException) { unavailable=true;items.build(y.getString("unavailable-item")!!,player) }
            val lines=lore.render(player,component.getStringList("lore"),values,q,ch,y.getString("reward-format"))
            if(unavailable) lines+=TextRenderer.noItalic(text.format("unavailable"))
            item.editMeta { meta -> meta.displayName(text.item(component.getString("name","")!!,values));meta.lore(lines) }
            if(packed) {
                if(card && cards!!.anchor(slot)) JournalCardVisuals.apply(item,y,q,ch)
                if(!card) JournalCardVisuals.control(item,component,s,when(action) {
                    "previous" -> s.page>0
                    "next" -> s.page<pages-1
                    else -> true
                })
            }
            contents[slot]=item
            action=when(action) {
                "journal-quest" -> q!!.takeIf { it.status==QuestStatus.ACTIVE }?.let { "track:${it.definition.id}" } ?: ""
                "journal-chapter" -> "chapter:${ch!!.definition.id}"
                "previous" -> if(s.page>0) action else ""
                "next" -> if(s.page<pages-1) action else ""
                else -> action
            }
            if(action.isNotBlank()) actions[slot]=action
        }
        y.getString("empty-slots")?.let { filler ->
            val item=items.build((if(player.hasResourcePack()) y.getString("components.$filler.pack-item") else null)
                ?: y.getString("components.$filler.item")!!,player)
            item.editMeta { meta ->
                meta.displayName(text.item(y.getString("components.$filler.name","")!!))
                meta.lore(y.getStringList("components.$filler.lore").map { text.item(it) })
            }
            for(slot in contents.indices) if(contents[slot]==null && (!pageOnly || slot in changedSlots)) contents[slot]=item
        }
        // Keep the open inventory and send only slots whose complete item/metadata changed.
        for(slot in contents.indices) if((!pageOnly || slot in changedSlots) && s.menu.getItem(slot)!=contents[slot]) s.menu.setItem(slot,contents[slot])
        s.actions.clear();s.actions.putAll(actions);s.revision++
    }
    private fun enabled(value: Boolean)=text.format(if(value) "journal.enabled" else "journal.disabled")
    fun loading(s: JournalSession,settings: Settings,key: String="journal.loading") {
        val y=settings.journalMenus.getValue(s.screen.file)
        s.menu.clear();s.actions.clear();s.revision++
        val item=items.build(y.getString("unavailable-item")!!)
        item.editMeta { it.displayName(TextRenderer.noItalic(text.format(key))) }
        s.menu.setItem(s.menu.size/2,item)
    }
}
