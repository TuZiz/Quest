package ym.quest.domain

data class ChapterDefinition(val id: String, val name: String, val icon: String,
    val description: List<String>, val order: Int, val prerequisites: List<String>,
    val quests: List<String>, val rewards: List<Reward>, val story: List<String> = emptyList(),
    val taskRequirements: Map<String, List<String>> = emptyMap(), val sourceFile: String = "chapters/$id",
    val optionalQuests: Set<String> = emptySet()) {
    val requiredQuests get() = quests.filterNot { it in optionalQuests }
}

enum class ChapterStatus { LOCKED, ACTIVE, COMPLETE }
data class ChapterView(val definition: ChapterDefinition, val status: ChapterStatus,
    val completed: Int, val claimed: Boolean)
data class JournalView(val quests: List<QuestView>, val chapters: List<ChapterView>)

/** Includes chapter gates in the DAG, so cross-chapter task cycles cannot stall silently. */
object ChapterGraph {
    fun prepare(quests: List<QuestDefinition>, chapters: List<ChapterDefinition>): List<QuestDefinition> {
        require(quests.isNotEmpty() && quests.map { it.id }.distinct().size == quests.size) { "quests/: empty or duplicate quest IDs" }
        require(chapters.map { it.id }.distinct().size == chapters.size) { "chapters/: duplicate chapter ID" }
        val owners = mutableMapOf<String, ChapterDefinition>()
        val byChapter = chapters.associateBy { it.id }
        val byQuest = quests.associateBy { it.id }
        chapters.forEach { ch ->
            require(ch.quests.isNotEmpty()) { "${ch.sourceFile}:tasks empty" }
            ch.quests.forEach { id ->
                require(id in byQuest) { "${ch.sourceFile}:tasks=$id missing" }
                require(owners.put(id,ch) == null) { "${ch.sourceFile}:tasks duplicate quest membership $id" }
            }
            ch.prerequisites.forEach { require(it in byChapter) { "${ch.sourceFile}:prerequisites=$it missing" } }
            require(ch.prerequisites.distinct().size == ch.prerequisites.size) { "${ch.sourceFile}:prerequisites duplicate" }
            ch.taskRequirements.forEach { (id, required) ->
                require(id in ch.quests && required.all { it in ch.quests }) { "${ch.sourceFile}:tasks.$id.requires=$required must reference tasks in the same chapter" }
                require(required.distinct().size == required.size) { "${ch.sourceFile}:tasks.$id.requires duplicate" }
            }
        }
        require(chapters.isEmpty() || owners.keys == byQuest.keys) { "chapters/: every quest must belong to one chapter" }
        val effective = quests.associate { q ->
            val ch = owners[q.id]
            q.id to q.copy(prerequisites=if(ch == null) q.prerequisites else ch.taskRequirements[q.id].orEmpty())
        }
        val visited = mutableSetOf<String>(); val active = mutableSetOf<String>(); val ordered = mutableListOf<QuestDefinition>()
        fun visit(node: String) {
            if(node in visited) return
            require(active.add(node)) { "${if(node.startsWith("chapter:")) byChapter[node.substringAfter(':')]?.sourceFile else owners[node.substringAfter(':')]?.sourceFile ?: "quests/"}: cyclic dependency at $node" }
            if(node.startsWith("chapter:")) {
                val ch = byChapter.getValue(node.substringAfter(':'))
                ch.prerequisites.forEach { visit("chapter:$it") }
                ch.quests.forEach { visit("quest:$it") }
            } else {
                val q = effective[node.substringAfter(':')] ?: error("quests: missing $node")
                q.prerequisites.forEach { visit("quest:$it") }
                owners[q.id]?.prerequisites?.forEach { visit("chapter:$it") }
                ordered += q
            }
            active.remove(node); visited.add(node)
        }
        chapters.sortedBy { it.order }.forEach { visit("chapter:${it.id}") }
        quests.forEach { visit("quest:${it.id}") }
        return ordered
    }
}
