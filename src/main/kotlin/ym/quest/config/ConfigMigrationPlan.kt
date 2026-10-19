package ym.quest.config

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.util.Properties

/** Durable journal: publish originals and outputs before touching active configuration. IO executor only. */
internal class ConfigMigrationPlan(private val root: Path, private val name: String = "chapter-v2") {
    data class Change(val path: String, val before: String?, val after: String?)
    private val journal = root.resolve(".migrations/$name")

    fun resume(): Boolean {
        if(!Files.exists(journal) || Files.exists(journal.resolve("complete"))) return false
        val manifest = Properties().apply { Files.newInputStream(journal.resolve("plan.properties")).use { load(it) } }
        val changes = (0 until manifest.getProperty("count").toInt()).map { i ->
            val path = manifest.getProperty("$i.path")
            require(!Path.of(path).isAbsolute && root.resolve(path).normalize().startsWith(root.normalize())) { "$name migration: invalid journal path $path" }
            fun read(kind: String) = journal.resolve("$i.$kind").takeIf { Files.exists(it) }?.let { Files.readString(it) }
            Change(path,read("before"),read("after"))
        }
        // Validate every path first. A user's edit during recovery is never overwritten.
        changes.forEach { c ->
            val file = root.resolve(c.path)
            val current = if(Files.exists(file)) Files.readString(file) else null
            require(current == c.before || current == c.after) { "${c.path}: schema 1 -> 2 migration recovery conflict; current file and journal originals preserved in $journal" }
        }
        changes.forEach { c ->
            val file = root.resolve(c.path)
            val current = if(Files.exists(file)) Files.readString(file) else null
            if(current != c.after) {
                if(c.after == null) Files.delete(file)
                else { Files.createDirectories(file.parent); ResourceUpgrade.replace(file,c.after) }
            }
        }
        Files.writeString(journal.resolve("complete"),"schema-version: 2\n")
        return true
    }

    fun apply(changes: List<Change>) {
        if(changes.isEmpty()) return
        require(!Files.exists(journal)) { "$journal: schema 1 -> 2 journal already exists; original configurations preserved" }
        Files.createDirectories(journal.parent)
        val stage = Files.createTempDirectory(journal.parent,".$name-")
        val manifest = Properties().apply { setProperty("count",changes.size.toString()) }
        changes.forEachIndexed { i,c ->
            manifest.setProperty("$i.path",c.path)
            c.before?.let { Files.writeString(stage.resolve("$i.before"),it) }
            c.after?.let { Files.writeString(stage.resolve("$i.after"),it) }
        }
        Files.newOutputStream(stage.resolve("plan.properties")).use { manifest.store(it,"Quest chapter configuration migration 1 -> 2") }
        Files.move(stage,journal,ATOMIC_MOVE)
        resume()
    }
}
