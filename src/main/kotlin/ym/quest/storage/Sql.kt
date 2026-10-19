package ym.quest.storage

import java.sql.Connection
import java.sql.ResultSet

internal fun Connection.update(sql: String, vararg args: Any?): Int = prepareStatement(sql).use { p ->
    args.forEachIndexed { index, value -> p.setObject(index+1,value) }; p.executeUpdate()
}
internal fun <T> Connection.rows(sql: String, vararg args: Any?, read: (ResultSet) -> T): List<T> = prepareStatement(sql).use { p ->
    args.forEachIndexed { index, value -> p.setObject(index+1,value) }
    p.executeQuery().use { rs -> buildList { while(rs.next()) add(read(rs)) } }
}
internal fun Connection.number(sql: String, vararg args: Any?): Long = rows(sql,*args) { it.getLong(1) }.firstOrNull() ?: 0
