package ym.quest.storage

import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.logging.Level
import java.util.logging.Logger

/** One executor owns JDBC. No Bukkit objects enter it. */
class Database(private val file: Path, private val log: Logger) : AutoCloseable {
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "Quest-sqlite") }
    private var connection: Connection? = null
    @Volatile private var closed = false
    private var closing: CompletableFuture<Unit>? = null
    fun open(): CompletableFuture<Unit> = submit {
        java.nio.file.Files.createDirectories(file.parent)
        Class.forName("org.sqlite.JDBC")
        connection = DriverManager.getConnection("jdbc:sqlite:$file")
        connection!!.createStatement().use { s ->
            s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA synchronous=FULL")
            s.execute("PRAGMA foreign_keys=ON"); s.execute("PRAGMA busy_timeout=5000")
            val version = s.executeQuery("PRAGMA user_version").use { it.next(); it.getInt(1) }
            require(version in 0..1) { "$file: schema-version=$version unsupported" }
            s.execute("CREATE TABLE IF NOT EXISTS facts(home TEXT,metric TEXT,value INTEGER NOT NULL CHECK(value>=0),PRIMARY KEY(home,metric))")
            s.execute("CREATE TABLE IF NOT EXISTS objectives(home TEXT,quest TEXT,objective TEXT,baseline INTEGER NOT NULL,progress INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(home,quest,objective))")
            s.execute("CREATE TABLE IF NOT EXISTS completions(home TEXT,quest TEXT,completed INTEGER NOT NULL,PRIMARY KEY(home,quest))")
            s.execute("CREATE TABLE IF NOT EXISTS claims(home TEXT,quest TEXT,player TEXT,created INTEGER,PRIMARY KEY(home,quest))")
            s.execute("CREATE TABLE IF NOT EXISTS parcels(id TEXT PRIMARY KEY,home TEXT NOT NULL,quest TEXT NOT NULL,item TEXT NOT NULL,amount INTEGER NOT NULL,phase TEXT NOT NULL,player TEXT)")
            s.execute("CREATE TABLE IF NOT EXISTS audit(created INTEGER NOT NULL,actor TEXT NOT NULL,action TEXT NOT NULL,details TEXT NOT NULL)")
            s.execute("PRAGMA user_version=1")
        }
    }
    fun <T> submit(action: () -> T): CompletableFuture<T> {
        if(closed) return CompletableFuture.failedFuture(IllegalStateException("Database closed"))
        return try { CompletableFuture.supplyAsync(action, worker) }
        catch(e: RejectedExecutionException) { CompletableFuture.failedFuture(e) }
    }
    fun <T> transaction(action: (Connection) -> T): T {
        val c = checkNotNull(connection)
        c.autoCommit = false
        return try { val result = action(c); c.commit(); result }
        catch(e: Throwable) { c.rollback(); throw e }
        finally { c.autoCommit = true }
    }
    @Synchronized fun closeAsync(): CompletableFuture<Unit> {
        closing?.let { return it }
        closed = true
        val result = CompletableFuture.supplyAsync({ connection?.close(); Unit },worker)
        closing=result
        result.whenComplete { _,e -> if(e != null) log.log(Level.SEVERE,"Closing Quest database $file failed",e) }
        worker.shutdown()
        return result
    }
    override fun close() { closeAsync() }
}
