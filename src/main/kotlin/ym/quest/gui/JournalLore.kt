package ym.quest.gui

import net.kyori.adventure.text.Component
import org.bukkit.entity.Player
import ym.quest.domain.*
import ym.quest.reward.ItemFactory
import ym.quest.text.TextRenderer

/** Expands list markers with their configured decoration, on the Paper owner thread. */
class JournalLore(private val items: ItemFactory, private val text: TextRenderer) {
    fun render(player: Player, templates: List<String>, values: Map<String,Component>,
               quest: QuestView?, chapter: ChapterView?, rewardFormat: String?): MutableList<Component> =
        templates.flatMap { line ->
            val marker=listOf("description","objectives","rewards").firstOrNull { "<$it>" in line }
            val entries=when(marker) {
            "description" -> (quest?.definition?.description ?: chapter?.definition?.description ?: emptyList()).flatMap {
                ym.quest.tab.TabTextWrap(56).lines(text.item(it,values)).map(TextRenderer::noItalic)
            }
            "objectives" -> quest?.progress?.map { p -> TextRenderer.noItalic(text.format("objective-line",mapOf(
                "description" to text.render(p.objective.description),"progress" to Component.text(p.progress),"target" to Component.text(p.objective.target)))) } ?: emptyList()
            "rewards" -> (quest?.definition?.rewards ?: chapter?.definition?.rewards ?: emptyList()).flatMap { reward ->
                var unavailable = false
                val name = try { items.build(reward.item,player).effectiveName() }
                    catch(e: IllegalStateException) { unavailable=true; Component.text(reward.item) }
                buildList {
                    add(text.item(checkNotNull(rewardFormat) { "Menu reward-format missing" },values+mapOf(
                        "reward" to name,"amount" to Component.text(reward.amount))))
                    if(unavailable) add(TextRenderer.noItalic(text.format("unavailable")))
                }
            }
            else -> null
            }
            if(marker==null) listOf(text.item(line,values))
            else entries.orEmpty().map { text.item(line,values+mapOf(marker to it)) }
        }.toMutableList()
}
