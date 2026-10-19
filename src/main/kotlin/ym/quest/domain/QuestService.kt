package ym.quest.domain

import ym.quest.storage.*
import java.util.concurrent.CompletableFuture

/** Domain entry point; arguments and results are immutable and contain no Bukkit references. */
class QuestService(private val db: Database) {
    private val progress = ProgressStore()
    private val rewards = RewardStore()
    @Volatile var definitions: List<QuestDefinition> = emptyList(); private set
    fun configure(next: List<QuestDefinition>) = db.submit { definitions = next.toList() }
    fun sync(home: String, facts: Map<String,Long>): CompletableFuture<List<ProgressChange>> = db.submit {
        db.transaction { c -> progress.facts(c,home,facts); progress.refresh(c,home,definitions) }
    }
    fun views(home: String): CompletableFuture<List<QuestView>> = db.submit {
        db.transaction { c -> progress.refresh(c,home,definitions); progress.views(c,home,definitions) }
    }
    fun claim(home: String, player: String, quest: String, facts: Map<String,Long>, hasGenerator: Boolean) = db.submit {
        db.transaction { c ->
            progress.facts(c,home,facts); progress.refresh(c,home,definitions)
            val view = progress.views(c,home,definitions).firstOrNull { it.definition.id == quest }
            val claimed = view != null && rewards.claim(c,home,player,view,hasGenerator)
            if(claimed) progress.refresh(c,home,definitions)
            claimed
        }
    }
    fun mailbox(home: String) = db.submit { db.transaction { rewards.parcels(it,home) } }
    fun transition(id: String, player: String, from: String, to: String) = db.submit { db.transaction { rewards.transition(it,id,player,from,to) } }
    fun reset(home: String, quest: String, actor: String, facts: Map<String,Long>) = db.submit {
        require(definitions.any { it.id == quest }) { "Unknown quest $quest" }
        db.transaction { c -> progress.facts(c,home,facts); progress.reset(c,home,quest,actor); progress.refresh(c,home,definitions); Unit }
    }
    fun resolve(id: String, actor: String, delivered: Boolean) = db.submit { db.transaction { rewards.resolve(it,id,actor,delivered) } }
}
