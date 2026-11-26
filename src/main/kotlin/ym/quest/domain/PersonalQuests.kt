package ym.quest.domain

import ym.quest.storage.*
import java.sql.Connection
import java.util.UUID

/** Database executor only. UUID ownership survives leaving or changing islands. */
class PersonalQuests {
    private val progress = ProgressStore()
    private val rewards = RewardStore()
    companion object {
        fun owner(player: String) = "player:${UUID.fromString(player)}"
    }
    private fun tasks(definitions: List<QuestDefinition>) = definitions.filter { it.scope == ProgressScope.PERSONAL }
        .map { q -> q.copy(prerequisites=q.prerequisites.filter { id -> definitions.any { it.id==id && it.scope==ProgressScope.PERSONAL } }) }
    private fun allowed(c: Connection, home: String, q: QuestDefinition, definitions: List<QuestDefinition>, chapters: List<ChapterDefinition>): Boolean {
        val original=definitions.single { it.id==q.id }
        return original.prerequisites.filter { id -> definitions.any { it.id==id && it.scope==ProgressScope.ISLAND } }
            .all { progress.satisfied(c,home,it) } &&
            (q.unlockWhen.isEmpty() || q.unlockWhen.any { (metric,target) -> c.number("SELECT value FROM facts WHERE home=? AND metric=?",home,metric)>=target }) &&
            (chapters.firstOrNull { q.id in it.quests }?.let { ChapterStore().eligible(c,home,it) } ?: true)
    }
    fun views(c: Connection, home: String, player: String, definitions: List<QuestDefinition>, chapters: List<ChapterDefinition>) =
        progress.views(c,owner(player),tasks(definitions)) { allowed(c,home,it,definitions,chapters) }
            .map { it.copy(definition=definitions.single { q -> q.id==it.definition.id }) }
    fun advance(c: Connection, home: String, player: String, definitions: List<QuestDefinition>, chapters: List<ChapterDefinition>, metric: String?=null, amount: Long=0): List<ProgressChange> {
        val key=owner(player); val tasks=tasks(definitions)
        val metrics=tasks.flatMap { it.objectives }.map { it.metric }.toSet()
        val before=metrics.associateWith { c.number("SELECT value FROM facts WHERE home=? AND metric=?",key,it) }
        fun refresh()=progress.refresh(c,key,tasks,metrics) { allowed(c,home,it,definitions,chapters) }
        progress.facts(c,key,before)
        val changes=refresh()
        if(metric==null) return changes
        require(amount>0 && metric.startsWith("combat."))
        progress.facts(c,key,mapOf(metric to Math.addExact(before[metric] ?: 0,amount)))
        return changes+refresh()
    }
    fun claim(c: Connection, home: String, player: String, definitions: List<QuestDefinition>, chapters: List<ChapterDefinition>, eligible: Set<String>, limit: Int): List<String> =
        views(c,home,player,definitions,chapters).filter { it.definition.id in eligible && it.status==QuestStatus.COMPLETE }.take(limit)
            .filter { rewards.claim(c,owner(player),player,it,false) }.map { it.definition.id }
}
