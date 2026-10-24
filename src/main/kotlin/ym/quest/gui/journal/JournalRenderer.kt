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
    fun render(player: Player,s: JournalSession,view: JournalView,prefs: DisplayPreferences,settings: Settings,pageOnly: Boolean=false) {
        val y=settings.journalMenus.getValue(s.screen.file)
        val shape=y.getStringList("Shape").joinToString("")
        val tracked=TrackingSelection.select(view,prefs)
        val chapter=view.chapters.firstOrNull { it.definition.id==s.chapter }
        val chapters=JournalNavigation.chapters(view,s)
        val tasks=JournalNavigation.tasks(view,s)
        val chapterList=s.screen==JournalPage.CHAPTERS
        val repeating=if(chapterList) "journal-chapter" else "journal-quest"
        val capacity=shape.count { y.getString("components.$it.action")==repeating }
        val count=if(chapterList) chapters.size else tasks.size
        val pages=if(capacity==0) 1 else ((count+capacity-1)/capacity).coerceAtLeast(1)
        s.pages=pages
        s.page=s.page.coerceIn(0,pages-1)
        val chapterIterator=chapters.drop(s.page*capacity).iterator()
        val taskIterator=tasks.drop(s.page*capacity).iterator()
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
                "journal-quest" -> if(taskIterator.hasNext()) taskIterator.next() else return@forEachIndexed
                else -> null
            }
            val ch=if(action=="journal-chapter") {
                if(chapterIterator.hasNext()) chapterIterator.next() else return@forEachIndexed
            } else chapter
            val values=mutableMapOf<String,Component>("page" to Component.text(s.page+1),"pages" to Component.text(pages),
                "category" to text.format("journal.category-${s.category}"),
                "info" to text.format(if(count==0) "journal.empty" else "journal.browse"),
                "quest" to (q?.let { text.render(it.definition.name) } ?: text.format("journal.no-selection")),
                "chapter" to (ch?.let { text.render(it.definition.name) } ?: text.format("journal.no-selection")),
                "status" to (q?.let { text.format("status.${it.status}") } ?: ch?.let { text.format("chapter-status.${it.status}") } ?: text.format("journal.empty")),
                "tracking" to text.format(if(q!=null && q.definition.id==tracked?.definition?.id) "journal.tracked" else "journal.not-tracked"),
                "progress" to Component.text(ch?.completed ?: 0),"target" to Component.text(ch?.definition?.requiredQuests?.size ?: 0),
                "percent" to Component.text(if(ch==null) 0 else ch.completed*100/ch.definition.requiredQuests.size.coerceAtLeast(1)),
                "hud-mode" to text.format("journal.mode-${prefs.mode}"),
                "auto" to enabled(prefs.autoTracking),"notifications" to enabled(prefs.notifications),"sound" to enabled(prefs.sound))
            val state=when(action) {
                "toggle-hud" -> prefs.mode!=HudMode.OFF
                "toggle-notices" -> prefs.notifications
                "toggle-sound" -> prefs.sound
                else -> null
            }
            val configured=if(player.hasResourcePack()) {
                (if(state!=null) component.getString(if(state) "item-on" else "item-off") else null)
                    ?: component.getString("pack-item")
            } else null
            val id=q?.definition?.icon ?: if(action=="journal-chapter") ch!!.definition.icon else configured ?: component.getString("item")!!
            var unavailable=false
            val item=try { items.build(id,player) } catch(e: IllegalStateException) { unavailable=true;items.build(y.getString("unavailable-item")!!,player) }
            val lines=lore.render(player,component.getStringList("lore"),values,q,ch,y.getString("reward-format"))
            if(unavailable) lines+=TextRenderer.noItalic(text.format("unavailable"))
            item.editMeta { meta -> meta.displayName(text.item(component.getString("name","")!!,values));meta.lore(lines) }
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
            val item=items.build(y.getString("components.$filler.item")!!,player)
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
