package ym.quest.introduction

import java.util.UUID

/** One interaction's completion capability; cancellation invalidates delayed native callbacks. */
class DialogueCompletion(private val finished: (Boolean) -> Unit) {
    val token: UUID=UUID.randomUUID()
    private var closed=false
    fun complete(candidate: UUID): Boolean {
        if(closed || candidate!=token) return false
        closed=true
        finished(true)
        return true
    }
    fun interrupt() {
        if(closed) return
        closed=true
        finished(false)
    }
    fun cancel() { closed=true }
}
