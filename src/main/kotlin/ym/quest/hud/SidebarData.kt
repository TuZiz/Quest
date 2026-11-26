package ym.quest.hud

import org.bukkit.entity.Player
import ym.quest.domain.*
import ym.quest.integration.Integrations
import ym.quest.preferences.DisplayPreferences
import ym.quest.preferences.HudMode
import ym.quest.text.TextRenderer
import java.util.concurrent.CompletableFuture

/** Read-only field values for external sidebars. Caller enters on the player thread. */
class SidebarData(private val service: QuestService, private val integrations: Integrations,
                  private val text: TextRenderer, private val preferences: (Player) -> DisplayPreferences?,
                  private val personal: (Player) -> JournalView?) {
    fun read(player: Player): CompletableFuture<Map<String,String>> {
        val prefs = preferences(player)
        val intro = personal(player)
        if (!player.hasPermission("quest.use") || prefs == null || prefs.mode == HudMode.OFF || intro?.quests?.isEmpty() == true)
            return CompletableFuture.completedFuture(emptyMap())
        val home = integrations.island(player.uniqueId)?.id
        if (home != null) return service.journal(home.toString(),player.uniqueId.toString()).thenApply { shared ->
            project(JournalView((intro?.quests.orEmpty()+shared.quests).distinctBy { it.definition.id },
                intro?.chapters.orEmpty()+shared.chapters),prefs)
        }
        if (intro != null) return CompletableFuture.completedFuture(project(intro,prefs))
        val first = service.definitions.firstOrNull { q -> q.category == QuestCategory.MAIN && q.objectives.any { it.type=="island.member" } }
        if (first == null || integrations.islands?.ready()!=true) return CompletableFuture.completedFuture(emptyMap())
        val quest = QuestView(first,QuestStatus.ACTIVE,first.objectives.filter { it.type=="island.member" }.map { ObjectiveProgress(it,0) })
        val chapters = service.chapters.filter { first.id in it.quests }.map { ChapterView(it,ChapterStatus.ACTIVE,0,false) }
        return CompletableFuture.completedFuture(project(JournalView(listOf(quest),chapters),prefs))
    }

    private fun project(journal: JournalView, prefs: DisplayPreferences): Map<String,String> =
        HudProjection(text,26).projectGrouped(journal,prefs.trackedQuest,prefs.autoTracking) ?: emptyMap()
}
