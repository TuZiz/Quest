package ym.quest.domain

import ym.quest.storage.*
import java.util.concurrent.CompletableFuture

/** Domain entry point; arguments and results are immutable and contain no Bukkit references. */
class QuestService(private val db: Database) {
    private val progress = ProgressStore()
    private val rewards = RewardStore()
    @Volatile var claimChanges: (List<ProgressChange>) -> Unit = {}
    private val chapterStore = ChapterStore()
    @Volatile var chapters: List<ChapterDefinition> = emptyList(); private set
    @Volatile var definitions: List<QuestDefinition> = emptyList(); private set
    @JvmOverloads fun configure(next: List<QuestDefinition>, nextChapters: List<ChapterDefinition> = emptyList()) = db.submit {
        val ordered = ChapterGraph.prepare(next,nextChapters)
        // Reconcile completed legacy islands without starting counters from cached facts.
        db.transaction { c ->
            val homes = c.rows("SELECT home FROM facts UNION SELECT home FROM objectives UNION SELECT home FROM completions UNION SELECT home FROM claims") { it.getString(1) }
            homes.forEach { home -> repeat(nextChapters.size+1) { chapterStore.refresh(c,home,nextChapters,progress) } }
        }
        chapters = nextChapters.toList(); definitions = ordered
    }
    private fun allowed(c: java.sql.Connection, home: String, q: QuestDefinition): Boolean =
        chapters.firstOrNull { q.id in it.quests }?.let { chapterStore.eligible(c,home,it) } ?: true
    private fun advance(c: java.sql.Connection, home: String, facts: Map<String,Long>): List<ProgressChange> {
        progress.facts(c,home,facts)
        val changes = mutableListOf<ProgressChange>()
        // Every pass uses the same committed absolute snapshot, including newly unlocked baselines.
        repeat(chapters.size+1) {
            val round = chapterStore.refresh(c,home,chapters,progress) +
                progress.refresh(c,home,definitions,facts.keys) { allowed(c,home,it) } +
                chapterStore.refresh(c,home,chapters,progress)
            changes += round
            if(round.isEmpty()) return changes
        }
        return changes
    }
    fun sync(home: String, facts: Map<String,Long>): CompletableFuture<List<ProgressChange>> = db.submit {
        db.transaction { c -> advance(c,home,facts) }
    }
    fun views(home: String): CompletableFuture<List<QuestView>> = db.submit {
        db.transaction { c -> progress.views(c,home,definitions) { allowed(c,home,it) } }
    }
    fun claim(home: String, player: String, quest: String, facts: Map<String,Long>, hasGenerator: Boolean) = db.submit {
        var changes = emptyList<ProgressChange>()
        val result = db.transaction { c ->
            changes = advance(c,home,facts)
            val view = progress.views(c,home,definitions) { allowed(c,home,it) }.firstOrNull { it.definition.id == quest }
            val claimed = view != null && rewards.claim(c,home,player,view,hasGenerator)
            claimed
        }
        claimChanges(changes); result
    }
    fun journal(home: String) = db.submit { db.transaction { c ->
        JournalView(progress.views(c,home,definitions) { allowed(c,home,it) },chapterStore.views(c,home,chapters,progress))
    } }
    fun claimChapter(home: String, player: String, chapter: String, facts: Map<String,Long>, hasGenerator: Boolean) = db.submit {
        var changes = emptyList<ProgressChange>()
        val result = db.transaction { c ->
            changes = advance(c,home,facts)
            val view = chapterStore.views(c,home,chapters,progress).firstOrNull { it.definition.id == chapter }
            if(view == null || view.status != ChapterStatus.COMPLETE || view.claimed) false
            else rewards.claim(c,home,player,QuestView(QuestDefinition("chapter:$chapter",view.definition.name,
                emptyList(),view.definition.icon,emptyList(),emptyList(),view.definition.rewards),QuestStatus.COMPLETE,emptyList()),hasGenerator)
        }
        claimChanges(changes); result
    }
    fun mailbox(home: String) = db.submit { db.transaction { rewards.parcels(it,home) } }
    /** Automatic grant uses the same unique claims and immutable delivery records as manual grants. */
    fun claimCompleted(home: String, player: String, facts: Map<String,Long>, hasGenerator: Boolean, eligible: Set<String>) = db.submit {
        var changes = emptyList<ProgressChange>()
        val claimed = db.transaction { c ->
            changes = advance(c,home,facts)
            val tasks = progress.views(c,home,definitions) { allowed(c,home,it) }
            val completedChapters = chapterStore.views(c,home,chapters,progress).filter { it.status == ChapterStatus.COMPLETE && !it.claimed }
                .map { view -> QuestView(QuestDefinition("chapter:${view.definition.id}",view.definition.name,emptyList(),
                    view.definition.icon,emptyList(),emptyList(),view.definition.rewards),QuestStatus.COMPLETE,emptyList()) }
            (tasks + completedChapters).filter { it.definition.id in eligible }
                .filter { rewards.claim(c,home,player,it,hasGenerator) }.map { it.definition.id }
        }
        claimChanges(changes); claimed
    }
    fun transition(id: String, player: String, from: String, to: String) = db.submit { db.transaction { rewards.transition(it,id,player,from,to) } }
    fun reset(home: String, quest: String, actor: String, facts: Map<String,Long>) = db.submit {
        require(definitions.any { it.id == quest }) { "Unknown quest $quest" }
        db.transaction { c -> progress.facts(c,home,facts); progress.reset(c,home,quest,actor); advance(c,home,facts); Unit }
    }
    fun resolve(id: String, actor: String, delivered: Boolean) = db.submit { db.transaction { rewards.resolve(it,id,actor,delivered) } }
}
