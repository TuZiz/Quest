package ym.quest.domain

import ym.quest.storage.*
import java.util.concurrent.CompletableFuture

/** Domain entry point; arguments and results are immutable and contain no Bukkit references. */
class QuestService(private val db: Database) {
    private val progress = ProgressStore()
    private val personal = PersonalQuests()
    private val shared get() = definitions.filter { it.scope==ProgressScope.ISLAND }
    private val rewards = RewardStore()
    @Volatile var claimChanges: (List<ProgressChange>) -> Unit = {}
    private val chapterStore = ChapterStore()
    @Volatile var chapters: List<ChapterDefinition> = emptyList(); private set
    @Volatile var definitions: List<QuestDefinition> = emptyList(); private set
    @JvmOverloads fun configure(next: List<QuestDefinition>, nextChapters: List<ChapterDefinition> = emptyList()) = db.submit {
        val ordered = ChapterGraph.prepare(next,nextChapters)
        val needsSnapshot=db.transaction { c -> ordered.any { it.scope==ProgressScope.PERSONAL &&
            c.number("SELECT COUNT(*) FROM audit WHERE action=?","personal-scope-v1:${it.id}")==0L } }
        if(needsSnapshot) db.snapshotOnce("before-personal-combat-v1")
        // Reconcile completed legacy islands without starting counters from cached facts.
        db.transaction { c ->
            TrialCombatProgressUpgrade.migrate(c,ordered)
            SharedEnergyProgressUpgrade.migrate(c,ordered)
            PersonalCombatProgressUpgrade.migrate(c,ordered)
            val homes = c.rows("SELECT home FROM facts UNION SELECT home FROM objectives UNION SELECT home FROM completions UNION SELECT home FROM claims") { it.getString(1) }
            homes.filterNot { it.startsWith("player:") }.forEach { home -> repeat(nextChapters.size+1) { chapterStore.refresh(c,home,nextChapters,progress) } }
        }
        chapters = nextChapters.toList(); definitions = ordered
    }
    private fun allowed(c: java.sql.Connection, home: String, q: QuestDefinition): Boolean =
        (q.unlockWhen.isEmpty() || q.unlockWhen.any { (metric,target) -> c.number("SELECT value FROM facts WHERE home=? AND metric=?",home,metric)>=target }) &&
        (chapters.firstOrNull { q.id in it.quests }?.let { chapterStore.eligible(c,home,it) } ?: true)
    private fun advance(c: java.sql.Connection, home: String, snapshot: Map<String,Long>): List<ProgressChange> {
        val facts=WarehouseProgressFacts.merge(c,home,snapshot)
        progress.facts(c,home,facts)
        val changes = mutableListOf<ProgressChange>()
        // Every pass uses the same committed absolute snapshot, including newly unlocked baselines.
        repeat(chapters.size+1) {
            val round = chapterStore.refresh(c,home,chapters,progress) +
                progress.refresh(c,home,shared,facts.keys) { allowed(c,home,it) } +
                chapterStore.refresh(c,home,chapters,progress)
            changes += round
            if(round.isEmpty()) return changes
        }
        return changes
    }
    fun sync(home: String, facts: Map<String,Long>): CompletableFuture<List<ProgressChange>> = db.submit {
        db.transaction { c -> advance(c,home,facts) }
    }
    /** Durable local successful actions. Initialize unlock baselines before incrementing the event. */
    fun record(home: String, metric: String, amount: Long, snapshot: Map<String,Long>) = db.submit {
        require(amount > 0)
        db.transaction { c ->
            val local = c.rows("SELECT metric,value FROM facts WHERE home=?",home) { it.getString(1) to it.getLong(2) }.toMap()
            val before = snapshot + local.filterKeys { it.startsWith("combat.") || it.startsWith("island.inspect") || it.startsWith("genlite.inspect") }
            val changes = advance(c,home,before + (metric to (local[metric] ?: 0L)))
            changes + advance(c,home,before + (metric to Math.addExact(local[metric] ?: 0L,amount)))
        }
    }
    fun views(home: String): CompletableFuture<List<QuestView>> = db.submit {
        db.transaction { c -> progress.views(c,home,shared) { allowed(c,home,it) } }
    }
    fun claim(home: String, player: String, quest: String, facts: Map<String,Long>, hasGenerator: Boolean) = db.submit {
        var changes = emptyList<ProgressChange>()
        val result = db.transaction { c ->
            changes = advance(c,home,facts)
            val isPersonal=definitions.any { it.id==quest && it.scope==ProgressScope.PERSONAL }
            if(isPersonal) changes=changes+personal.advance(c,home,player,definitions,chapters)
            val view = (if(isPersonal) personal.views(c,home,player,definitions,chapters) else progress.views(c,home,shared) { allowed(c,home,it) }).firstOrNull { it.definition.id == quest }
            val claimed = view != null && rewards.claim(c,if(isPersonal) PersonalQuests.owner(player) else home,player,view,hasGenerator)
            claimed
        }
        claimChanges(changes); result
    }
    @JvmOverloads fun journal(home: String, player: String? = null) = db.submit { db.transaction { c ->
        if(player!=null) personal.advance(c,home,player,definitions,chapters)
        val views=progress.views(c,home,shared) { allowed(c,home,it) } +
            if(player==null) emptyList() else personal.views(c,home,player,definitions,chapters)
        JournalView(views.sortedBy { v -> definitions.indexOfFirst { it.id==v.definition.id } },chapterStore.views(c,home,chapters,progress))
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
    fun recordPersonal(home: String, player: String, metric: String, amount: Long) = db.submit {
        db.transaction { c -> personal.advance(c,home,player,definitions,chapters,metric,amount) }
    }
    fun claimPersonalCompleted(home: String, player: String, eligible: Set<String>, limit: Int = 1) = db.submit {
        var changes=emptyList<ProgressChange>()
        val result=db.transaction { c ->
            changes=personal.advance(c,home,player,definitions,chapters)
            personal.claim(c,home,player,definitions,chapters,eligible,limit)
        }
        claimChanges(changes); result
    }
    fun mailbox(home: String) = db.submit { db.transaction { rewards.parcels(it,home) } }
    /** Automatic grant uses the same unique claims and immutable delivery records as manual grants. */
    fun claimCompleted(home: String, player: String, facts: Map<String,Long>, hasGenerator: Boolean, eligible: Set<String>, limit: Int = Int.MAX_VALUE) = db.submit {
        require(limit > 0)
        var changes = emptyList<ProgressChange>()
        val claimed = db.transaction { c ->
            changes = advance(c,home,facts)
            val tasks = progress.views(c,home,shared) { allowed(c,home,it) }
            val completedChapters = chapterStore.views(c,home,chapters,progress).filter { it.status == ChapterStatus.COMPLETE && !it.claimed }
                .map { view -> QuestView(QuestDefinition("chapter:${view.definition.id}",view.definition.name,emptyList(),
                    view.definition.icon,emptyList(),emptyList(),view.definition.rewards),QuestStatus.COMPLETE,emptyList()) }
            (tasks + completedChapters).filter { it.definition.id in eligible && it.status == QuestStatus.COMPLETE }.take(limit)
                .filter { rewards.claim(c,home,player,it,hasGenerator) }.map { it.definition.id }
        }
        claimChanges(changes); claimed
    }
    fun transition(id: String, player: String, from: String, to: String) = db.submit { db.transaction { rewards.transition(it,id,player,from,to) } }
    fun reset(home: String, quest: String, actor: String, facts: Map<String,Long>) = db.submit {
        require(shared.any { it.id == quest }) { "Island reset requires an island-scoped quest: $quest; personal UUID progress retained" }
        db.transaction { c -> progress.facts(c,home,facts); progress.reset(c,home,quest,actor); advance(c,home,facts); Unit }
    }
    fun resolve(id: String, actor: String, delivered: Boolean) = db.submit { db.transaction { rewards.resolve(it,id,actor,delivered) } }
}
