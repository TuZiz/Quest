package ym.quest.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.quest.domain.*
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer

/** Expands whole-line list markers using actual registered item names, on the Paper owner thread. */
class JournalLore(private val items: ItemFactory, private val text: TextRenderer) {
    fun render(player: Player, templates: List<String>, values: Map<String,Component>,
               quest: QuestView?, chapter: ChapterView?, rewardFormat: String?): MutableList<Component> =
        templates.flatMap { line -> when(line.trim()) {
            "<description>" -> (quest?.definition?.description ?: chapter?.definition?.description ?: emptyList()).map { text.item(it,values) }
            "<objectives>" -> quest?.progress?.map { p -> TextRenderer.noItalic(text.format("objective-line",mapOf(
                "description" to text.render(p.objective.description),"progress" to Component.text(p.progress),"target" to Component.text(p.objective.target)))) } ?: emptyList()
            "<rewards>" -> (quest?.definition?.rewards ?: chapter?.definition?.rewards ?: emptyList()).flatMap { reward ->
                var unavailable = false
                val name = try { items.build(reward.item,player).effectiveName() }
                    catch(e: IllegalStateException) { unavailable=true; Component.text(reward.item) }
                buildList {
                    add(text.item(checkNotNull(rewardFormat) { "gui/main.yml:reward-format missing" },values+mapOf(
                        "reward" to name,"amount" to Component.text(reward.amount))))
                    if(unavailable) add(TextRenderer.noItalic(text.format("unavailable")))
                }
            }
            else -> listOf(text.item(line,values))
        } }.toMutableList()
}
