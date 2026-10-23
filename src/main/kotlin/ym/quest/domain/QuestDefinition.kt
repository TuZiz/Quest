package ym.quest.domain

data class Objective(val id: String, val type: String, val target: Long, val resource: String?, val description: String,
                     val metric: String = if (resource == null) type else "$type:$resource",
                     val state: Boolean = type in setOf("island.member", "genlite.placed", "genlite.level", "genlite.collector"))
data class Reward(val item: String, val amount: Int, val starter: Boolean = false, val previouslyRewardedBy: String? = null)
enum class QuestCategory { MAIN, SIDE, SPECIAL }
data class QuestDefinition @JvmOverloads constructor(val id: String, val name: String, val description: List<String>, val icon: String,
                           val prerequisites: List<String>, val objectives: List<Objective>, val rewards: List<Reward>,
                           val category: QuestCategory = QuestCategory.MAIN,
                           val unlockWhen: Map<String,Long> = emptyMap())
data class ObjectiveProgress(val objective: Objective, val progress: Long)
enum class QuestStatus { LOCKED, ACTIVE, COMPLETE, CLAIMED }
data class QuestView(val definition: QuestDefinition, val status: QuestStatus, val progress: List<ObjectiveProgress>)
data class Parcel(val id: String, val home: String, val quest: String, val item: String, val amount: Int,
                  val phase: String, val player: String?)
data class ProgressChange(val home: String, val quest: String, val completed: Boolean, val started: Boolean = false, val chapter: Boolean = false)
enum class ProgressScope { ISLAND, PERSONAL }
interface ConditionType { val id: String; fun metric(resource: String?): String }
class ConditionRegistry {
    private val types = java.util.concurrent.ConcurrentHashMap<String, ConditionType>()
    init { listOf("island.member", "genlite.placed", "genlite.fuel", "genlite.produce", "genlite.pickup", "genlite.level", "genlite.collector", "genlite.bind", "genlite.transfer", "genlite.extract", "genlite.mode", "genlite.inspect", "island.inspect", "combat.kill").forEach { type ->
        register(object : ConditionType { override val id = type
            override fun metric(resource: String?) = if(resource == null) id else "$id:$resource" })
    } }
    fun register(type: ConditionType) { require(types.putIfAbsent(type.id, type) == null) }
    fun contains(id: String) = types.containsKey(id)
    fun metric(id: String, resource: String?) = checkNotNull(types[id]).metric(resource)
}
