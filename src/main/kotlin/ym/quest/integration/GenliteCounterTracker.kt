package ym.quest.integration

/** Current-state snapshots may decrease; committed action totals must remain monotonic. */
internal class GenliteCounterTracker {
    enum class Result { CHANGED, UNCHANGED, ROLLBACK }
    private val seen=java.util.concurrent.ConcurrentHashMap<String,Long>()
    fun previous(key: String)=seen[key]
    fun accept(key: String,value: Long): Result {
        val old=seen[key]
        if(old==value) return Result.UNCHANGED
        val metric=key.substringAfter('|').substringBefore(':')
        val live=metric in setOf("genlite.connected","genlite.level","genlite.placed","genlite.collector")
        if(old!=null && value<old && !live) return Result.ROLLBACK
        seen[key]=value;return Result.CHANGED
    }
    fun remove(key: String) {seen.remove(key)}
    fun clear() {seen.clear()}
}
