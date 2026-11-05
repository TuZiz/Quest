package ym.quest

import com.google.gson.JsonParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ym.quest.introduction.DialogueCompletion
import java.nio.file.Files
import java.nio.file.Path
import java.util.UUID

class TypewriterDialogueTest {
    @Test fun `native completion requires current token and can run only once`() {
        val outcomes=mutableListOf<Boolean>()
        val first=DialogueCompletion(outcomes::add)
        assertFalse(first.complete(UUID.randomUUID()))
        assertTrue(outcomes.isEmpty())
        assertTrue(first.complete(first.token))
        assertFalse(first.complete(first.token))
        first.interrupt()
        assertEquals(listOf(true),outcomes)
    }
    @Test fun `cancel interruption replay and delayed previous callback cannot complete a new session`() {
        val outcomes=mutableListOf<Boolean>()
        val canceled=DialogueCompletion(outcomes::add)
        canceled.cancel()
        assertFalse(canceled.complete(canceled.token))
        val interrupted=DialogueCompletion(outcomes::add)
        interrupted.interrupt();interrupted.interrupt()
        assertFalse(interrupted.complete(interrupted.token))
        val next=DialogueCompletion(outcomes::add)
        assertFalse(next.complete(canceled.token))
        assertTrue(next.complete(next.token))
        assertEquals(listOf(false,true),outcomes)
    }
    @Test fun `published dialogue uses native spoken chain speaker and guarded terminal action`() {
        val root=Path.of("integrations/typewriter/pages")
        val entries=Files.list(root).use { paths -> paths.flatMap { path ->
            JsonParser.parseString(Files.readString(path)).asJsonObject.getAsJsonArray("entries").toList().stream()
        }.toList() }.associate { it.asJsonObject.get("id").asString to it.asJsonObject }
        assertEquals(9,entries.size)
        var current="quest_guide_01"
        val visited=mutableSetOf<String>()
        while(current!="quest_guide_complete") {
            assertTrue(visited.add(current))
            val entry=entries.getValue(current)
            assertEquals("spoken",entry.get("type").asString)
            assertEquals("quest_guide_speaker",entry.get("speaker").asString)
            assertTrue(entry.get("text").asString.contains("<#"))
            current=entry.getAsJsonArray("triggers").single().asString
        }
        assertEquals(5,visited.size)
        val command=entries.getValue(current)
        assertEquals("console_run_command",command.get("type").asString)
        assertEquals("quest_guide_context_command",command.getAsJsonObject("command").get("ref").asString)
        assertEquals("entry",command.getAsJsonObject("command").getAsJsonObject("data").getAsJsonObject("key").get("case").asString)
        assertEquals("interaction_context_string_variable",entries.getValue("quest_guide_context_command").get("type").asString)
        assertEquals("simple_speaker",entries.getValue("quest_guide_speaker").get("type").asString)
    }
}
