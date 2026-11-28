package com.example.mybeaver.sql

import com.example.mybeaver.exceptions.InvalidConnectionDataException
import com.example.mybeaver.jdbc.ConnectionProvider
import org.springframework.stereotype.Service
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Statement

@Service
class SqlService(private val provider: ConnectionProvider) {

    fun execute(connectionId: String, request: SqlRequest): ScriptResult {
        val chunks = SqlScriptSplitter.split(request.sql)
        if (chunks.isEmpty()) throw InvalidConnectionDataException("No hay ninguna sentencia para ejecutar.")
        val maxRows = request.maxRows.coerceIn(1, 5000)

        val results = mutableListOf<StatementResult>()
        var failed = false
        val scriptStart = System.nanoTime()

        provider.open(connectionId, request.database, allowMultiQueries = true).use { con ->
            con.createStatement().use { st ->
                st.maxRows = maxRows + 1
                for (chunk in chunks) {
                    val start = System.nanoTime()
                    val firstOfChunk = results.size
                    try {
                        runChunk(st, chunk, maxRows, results)
                        if (results.size > firstOfChunk) {
                            results[firstOfChunk] = results[firstOfChunk].copy(elapsedMs = millisSince(start))
                        }
                    } catch (e: SQLException) {
                        results.add(
                            StatementResult(
                                index = results.size + 1,
                                sql = preview(chunk.sql),
                                kind = ResultKind.ERROR,
                                message = "Error ${e.errorCode} (${e.sqlState}): ${e.message}",
                                elapsedMs = millisSince(start)
                            )
                        )
                        failed = true
                        break
                    }
                }
            }
        }
        return ScriptResult(results, failed, millisSince(scriptStart))
    }

    private fun runChunk(st: Statement, chunk: ScriptChunk, maxRows: Int, out: MutableList<StatementResult>) {
        val text = preview(chunk.sql)
        var isResultSet = st.execute(chunk.sql)
        while (true) {
            if (isResultSet) {
                st.resultSet.use { rs -> out.add(readResultSet(rs, out.size + 1, text, maxRows)) }
            } else {
                val count = st.largeUpdateCount
                if (count == -1L) break
                out.add(StatementResult(out.size + 1, text, ResultKind.UPDATE, updateCount = count))
            }
            isResultSet = st.moreResults
        }
    }

    private fun readResultSet(rs: ResultSet, index: Int, sql: String, maxRows: Int): StatementResult {
        val md = rs.metaData
        val columns = (1..md.columnCount).map { md.getColumnLabel(it) }
        val rows = mutableListOf<List<String?>>()
        var truncated = false
        while (rs.next()) {
            if (rows.size >= maxRows) { truncated = true; break }
            rows.add((1..columns.size).map { cell(rs, it) })
        }
        return StatementResult(index, sql, ResultKind.RESULT_SET, columns, rows, truncated)
    }

    private fun cell(rs: ResultSet, i: Int): String? {
        val value = rs.getObject(i) ?: return null
        val text = when (value) {
            is ByteArray ->
                if (value.size <= 32) "0x" + value.joinToString("") { "%02X".format(it) }
                else "[BINARIO ${value.size} bytes]"
            is Boolean -> if (value) "1" else "0"
            else -> rs.getString(i) ?: value.toString()
        }
        return if (text.length > MAX_CELL) text.take(MAX_CELL) + "…" else text
    }

    private fun preview(sql: String) = if (sql.length > 300) sql.take(300) + "…" else sql

    private fun millisSince(start: Long) = (System.nanoTime() - start) / 1_000_000

    private companion object {
        const val MAX_CELL = 2000
    }
}
