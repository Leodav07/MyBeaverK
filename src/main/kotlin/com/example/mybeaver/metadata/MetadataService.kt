package com.example.mybeaver.metadata

import com.example.mybeaver.exceptions.InvalidConnectionDataException
import com.example.mybeaver.exceptions.NotApplicableException
import com.example.mybeaver.exceptions.ObjectNotFoundException
import com.example.mybeaver.exceptions.PermissionDeniedException
import com.example.mybeaver.jdbc.SqlNames
import com.example.mybeaver.jdbc.ConnectionProvider
import org.springframework.stereotype.Service
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException


@Service
class MetadataService(private val provider: ConnectionProvider) {


    fun listDatabases(connectionId: String): List<String> =
        provider.open(connectionId).use { con ->
            val result = mutableListOf<String>()
            con.createStatement().use { st ->
                st.executeQuery("SHOW DATABASES").use { rs -> while (rs.next()) result.add(rs.getString(1)) }
            }
            result
        }


    fun listObjects(connectionId: String, database: String, type: ObjectType): List<DbObject> {
        if (!type.supported) throw NotApplicableException(type.note ?: "Este objeto no existe en MySQL.")
        return provider.open(connectionId, database).use { con ->
            when (type) {
                ObjectType.TABLE -> listTables(con, "BASE TABLE")
                ObjectType.VIEW -> listTables(con, "VIEW")
                ObjectType.PROCEDURE -> listRoutines(con, "PROCEDURE", database)
                ObjectType.FUNCTION -> listRoutines(con, "FUNCTION", database)
                ObjectType.TRIGGER -> listTriggers(con)
                ObjectType.EVENT -> listEvents(con)
                ObjectType.INDEX -> listAllIndexes(con)
                ObjectType.SEQUENCE -> listAutoIncrements(con)
                ObjectType.USER -> listUsers(con)
                ObjectType.TABLESPACE -> readTablespaces(con).map {
                    DbObject(it.name, "TABLESPACE", "${it.kind} · ${it.file}")
                }
                ObjectType.PACKAGE -> emptyList()
            }
        }
    }


    private fun listTables(con: Connection, tableType: String): List<DbObject> =
        tableNames(con, tableType).map { DbObject(it, tableType) }

    private fun tableNames(con: Connection, tableType: String): List<String> {
        val result = mutableListOf<String>()
        con.createStatement().use { st ->
            st.executeQuery("SHOW FULL TABLES WHERE Table_type = '$tableType'").use { rs ->
                while (rs.next()) result.add(rs.getString(1))
            }
        }
        return result
    }


    private fun listRoutines(con: Connection, kind: String, database: String): List<DbObject> {
        val result = mutableListOf<DbObject>()
        con.prepareStatement("SHOW $kind STATUS WHERE Db = ?").use { ps ->
            ps.setString(1, database)
            ps.executeQuery().use { rs ->
                while (rs.next()) {
                    result.add(DbObject(rs.getString("Name"), kind, "Definer: " + rs.getString("Definer")))
                }
            }
        }
        return result
    }

    private fun listTriggers(con: Connection): List<DbObject> {
        val result = mutableListOf<DbObject>()
        con.createStatement().use { st ->
            st.executeQuery("SHOW TRIGGERS").use { rs ->
                while (rs.next()) {
                    val table = rs.getString("Table")
                    val detail = rs.getString("Timing") + " " + rs.getString("Event") + " ON " + table
                    result.add(DbObject(rs.getString("Trigger"), "TRIGGER", detail, table))
                }
            }
        }
        return result
    }

    private fun listEvents(con: Connection): List<DbObject> {
        val result = mutableListOf<DbObject>()
        con.createStatement().use { st ->
            st.executeQuery("SHOW EVENTS").use { rs ->
                while (rs.next()) {
                    val detail = rs.getString("Type") + " · " + rs.getString("Status")
                    result.add(DbObject(rs.getString("Name"), "EVENT", detail))
                }
            }
        }
        return result
    }

    private fun listAllIndexes(con: Connection): List<DbObject> =
        tableNames(con, "BASE TABLE").flatMap { table ->
            readIndexes(con, table).map { idx ->
                val kind = when {
                    idx.name == "PRIMARY" -> "PRIMARY KEY"
                    idx.unique -> "UNIQUE"
                    else -> idx.type
                }
                DbObject(idx.name, "INDEX", "$table · $kind (${idx.columns.joinToString(", ")})", table)
            }
        }


    private fun listAutoIncrements(con: Connection): List<DbObject> =
        tableNames(con, "BASE TABLE").mapNotNull { table ->
            val ai = autoIncrementOf(con, table) ?: return@mapNotNull null
            DbObject(table, "SEQUENCE", "columna ${ai.column} · siguiente valor ${ai.nextValue}", table)
        }

    private data class AutoIncrement(val column: String, val nextValue: Long)

    private fun autoIncrementOf(con: Connection, table: String): AutoIncrement? {
        val ddl = showCreate(con, "SHOW CREATE TABLE ${SqlNames.quote(table)}", "Create Table", table)
        val column = AI_COLUMN.find(ddl)?.groupValues?.get(1)?.replace("``", "`") ?: return null
        val next = AI_VALUE.find(ddl)?.groupValues?.get(1)?.toLong() ?: 1L
        return AutoIncrement(column, next)
    }

    private fun listUsers(con: Connection): List<DbObject> {
        val result = mutableListOf<DbObject>()
        con.createStatement().use { st ->
            st.executeQuery("SELECT User, Host FROM mysql.user ORDER BY User, Host").use { rs ->
                while (rs.next()) {
                    val user = rs.getString("User")
                    val host = rs.getString("Host")
                    result.add(DbObject("$user@$host", "USER", null))
                }
            }
        }
        return result
    }


    private data class Tablespace(
        val name: String, val kind: String, val file: String,
        val table: Pair<String, String>? = null, val dataFile: String = file
    )


    private fun readTablespaces(con: Connection): List<Tablespace> {
        val datadir = variable(con, "datadir")?.let(::normalizePath)?.trimEnd('/') ?: ""
        val files = mutableListOf<String>()
        try {
            con.createStatement().use { st ->
                st.executeQuery(
                    "SELECT FILE_NAME FROM performance_schema.file_instances " +
                        "WHERE EVENT_NAME = 'wait/io/file/innodb/innodb_data_file' ORDER BY FILE_NAME"
                ).use { rs -> while (rs.next()) files.add(rs.getString(1)) }
            }
        } catch (e: SQLException) {
            return listOf(
                Tablespace("innodb_system", "Sistema", variable(con, "innodb_data_file_path") ?: "ibdata1", null),
                Tablespace(
                    "innodb_temporary", "Temporal",
                    variable(con, "innodb_temp_data_file_path") ?: "ibtmp1", null
                )
            )
        }

        return files.map { raw ->
            val full = normalizePath(raw)
            val relative = (if (datadir.isNotEmpty() && full.startsWith("$datadir/", ignoreCase = true)) full.substring(datadir.length + 1)
            else full).removePrefix("./")
            val fileName = relative.substringAfterLast('/')
            val parts = relative.split('/')
            when {
                fileName.startsWith("ibdata") -> Tablespace("innodb_system", "Sistema", raw, null)
                fileName.startsWith("ibtmp") -> Tablespace("innodb_temporary", "Temporal", raw, null)
                relative.startsWith("#innodb_temp/") -> Tablespace("temp/" + fileName.substringBeforeLast('.'), "Temporal de sesión", raw, null)
                fileName == "mysql.ibd" && parts.size == 1 -> Tablespace("mysql", "Diccionario de datos", raw, null)
                fileName.startsWith("undo_") || fileName.endsWith(".ibu") ->
                    Tablespace(fileName.substringBeforeLast('.'), "Undo", raw, null)
                parts.size == 2 && fileName.endsWith(".ibd") -> {
                    val db = decodeFsName(parts[0])
                    val table = decodeFsName(fileName.removeSuffix(".ibd").substringBefore("#"))
                    Tablespace("$db/$table", "Archivo por tabla", raw, db to table)
                }
                else -> Tablespace(fileName.substringBeforeLast('.'), "General", raw, null, relative)
            }
        }.distinctBy { it.name.lowercase() }
    }

    private fun decodeFsName(name: String): String =
        Regex("@([0-9a-fA-F]{4})").replace(name) { it.groupValues[1].toInt(16).toChar().toString() }

    private fun normalizePath(path: String) = path.replace('\\', '/')

    private fun variable(con: Connection, name: String): String? =
        con.prepareStatement("SHOW VARIABLES LIKE ?").use { ps ->
            ps.setString(1, name)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString("Value") else null }
        }

    fun ddl(connectionId: String, database: String, type: ObjectType, name: String, table: String?): String {
        if (!type.supported) throw NotApplicableException(type.note ?: "Este objeto no existe en MySQL.")
        return provider.open(connectionId, database).use { con ->
            val quoted = SqlNames.quote(name)
            when (type) {
                ObjectType.TABLE -> showCreate(con, "SHOW CREATE TABLE $quoted", "Create Table", name) + ";"
                ObjectType.VIEW -> showCreate(con, "SHOW CREATE VIEW $quoted", "Create View", name) + ";"
                ObjectType.PROCEDURE -> routineDdl(showCreate(con, "SHOW CREATE PROCEDURE $quoted", "Create Procedure", name))
                ObjectType.FUNCTION -> routineDdl(showCreate(con, "SHOW CREATE FUNCTION $quoted", "Create Function", name))
                ObjectType.TRIGGER -> routineDdl(showCreate(con, "SHOW CREATE TRIGGER $quoted", "SQL Original Statement", name))
                ObjectType.EVENT -> routineDdl(showCreate(con, "SHOW CREATE EVENT $quoted", "Create Event", name))
                ObjectType.INDEX -> indexDdl(con, table ?: throw InvalidConnectionDataException("Falta la tabla del índice."), name)
                ObjectType.SEQUENCE -> sequenceDdl(con, name)
                ObjectType.USER -> userDdl(con, name)
                ObjectType.TABLESPACE -> tablespaceDdl(con, name)
                ObjectType.PACKAGE -> ""
            }
        }
    }

    private fun showCreate(con: Connection, sql: String, column: String, name: String): String =
        con.createStatement().use { st ->
            st.executeQuery(sql).use { rs ->
                if (!rs.next()) throw ObjectNotFoundException(name)
                rs.getString(column) ?: throw PermissionDeniedException(
                    "MySQL no devolvió el código de \"$name\": tu usuario no es el DEFINER ni tiene el privilegio " +
                        "SHOW_ROUTINE / SELECT sobre mysql para verlo."
                )
            }
        }

    private fun routineDdl(body: String) = "DELIMITER $$\n$body$$\nDELIMITER ;"

    private fun indexDdl(con: Connection, table: String, index: String): String {
        val rows = readIndexRows(con, table)[index] ?: throw ObjectNotFoundException("$table.$index")
        val qt = SqlNames.quote(table)
        val parts = rows.joinToString(", ") { r ->
            if (r.expression != null) "(${r.expression})"
            else SqlNames.quote(r.column!!) + (r.subPart?.let { "($it)" } ?: "") + (if (r.desc) " DESC" else "")
        }
        val first = rows.first()
        if (index == "PRIMARY") return "ALTER TABLE $qt ADD PRIMARY KEY ($parts);"

        val kind = when {
            first.indexType == "FULLTEXT" -> "FULLTEXT "
            first.indexType == "SPATIAL" -> "SPATIAL "
            first.unique -> "UNIQUE "
            else -> ""
        }
        val using = if (first.indexType == "HASH") " USING HASH" else ""
        val comment = first.comment?.takeIf { it.isNotEmpty() }?.let { " COMMENT " + SqlNames.literal(it) } ?: ""
        val invisible = if (first.visible == false) " INVISIBLE" else ""
        return "CREATE ${kind}INDEX ${SqlNames.quote(index)}$using ON $qt ($parts)$comment$invisible;"
    }

    private fun sequenceDdl(con: Connection, table: String): String {
        val ai = autoIncrementOf(con, table) ?: throw ObjectNotFoundException(table)
        val column = listColumns(con, table).first { it.name == ai.column }
        val nullability = if (column.nullable) "NULL" else "NOT NULL"
        val qt = SqlNames.quote(table)
        return """
            |-- MySQL no tiene CREATE SEQUENCE: el generador es la columna AUTO_INCREMENT.
            |-- Tabla: $table   Columna: ${ai.column}   Siguiente valor: ${ai.nextValue}
            |ALTER TABLE $qt MODIFY ${SqlNames.quote(column.name)} ${column.type} $nullability AUTO_INCREMENT;
            |ALTER TABLE $qt AUTO_INCREMENT = ${ai.nextValue};
        """.trimMargin()
    }

    private fun userDdl(con: Connection, account: String): String {
        val at = account.lastIndexOf('@')
        if (at < 0) throw InvalidConnectionDataException("El usuario debe tener la forma usuario@host.")
        val user = account.substring(0, at)
        val host = account.substring(at + 1)

        val create = con.prepareStatement("SHOW CREATE USER ?@?").use { ps ->
            ps.setString(1, user); ps.setString(2, host)
            ps.executeQuery().use { rs -> if (rs.next()) rs.getString(1) else throw ObjectNotFoundException(account) }
        }
        val grants = mutableListOf<String>()
        con.prepareStatement("SHOW GRANTS FOR ?@?").use { ps ->
            ps.setString(1, user); ps.setString(2, host)
            ps.executeQuery().use { rs -> while (rs.next()) grants.add(rs.getString(1) + ";") }
        }
        return "$create;\n\n-- Privilegios (SHOW GRANTS)\n" + grants.joinToString("\n")
    }

    private fun tablespaceDdl(con: Connection, name: String): String {
        val ts = readTablespaces(con).firstOrNull { it.name == name } ?: throw ObjectNotFoundException(name)
        val header = "-- Tablespace: ${ts.name}\n-- Tipo: ${ts.kind}\n-- Archivo: ${ts.file}\n"
        return header + when (ts.kind) {
            "General" ->
                "CREATE TABLESPACE ${SqlNames.quote(ts.name)} ADD DATAFILE ${SqlNames.literal(ts.dataFile)} ENGINE = InnoDB;"
            "Archivo por tabla" -> {
                val (db, table) = ts.table!!
                "-- MySQL lo crea automáticamente junto con la tabla (innodb_file_per_table = ON).\n" +
                    "-- Su DDL es el CREATE TABLE de la tabla:\n" +
                    showCreate(con, "SHOW CREATE TABLE ${SqlNames.quote(db)}.${SqlNames.quote(table)}", "Create Table", table) + ";"
            }
            "Sistema" -> "-- Se define al instalar el servidor con la variable innodb_data_file_path:\n" +
                "-- innodb_data_file_path = ${variable(con, "innodb_data_file_path")}"
            "Temporal" -> "-- Se define con la variable innodb_temp_data_file_path:\n" +
                "-- innodb_temp_data_file_path = ${variable(con, "innodb_temp_data_file_path")}"
            "Undo" -> "-- Tablespace de UNDO. Los que crea el usuario se crean así:\n" +
                "-- CREATE UNDO TABLESPACE ${SqlNames.quote(ts.name)} ADD DATAFILE '${ts.name}.ibu';"
            else -> "-- Tablespace interno de MySQL: no tiene sentencia CREATE (lo gestiona el servidor)."
        }
    }

    fun listColumns(connectionId: String, database: String, table: String): List<ColumnInfo> =
        provider.open(connectionId, database).use { con -> listColumns(con, table) }

    private fun listColumns(con: Connection, table: String): List<ColumnInfo> {
        val result = mutableListOf<ColumnInfo>()
        con.createStatement().use { st ->
            st.executeQuery("SHOW FULL COLUMNS FROM ${SqlNames.quote(table)}").use { rs ->
                while (rs.next()) {
                    result.add(
                        ColumnInfo(
                            name = rs.getString("Field"),
                            type = rs.getString("Type"),
                            nullable = rs.getString("Null") == "YES",
                            key = rs.getString("Key") ?: "",
                            default = rs.getString("Default"),
                            extra = rs.getString("Extra") ?: "",
                            comment = rs.getString("Comment") ?: ""
                        )
                    )
                }
            }
        }
        return result
    }

    fun listIndexes(connectionId: String, database: String, table: String): List<IndexInfo> =
        provider.open(connectionId, database).use { con -> readIndexes(con, table) }

    private fun readIndexes(con: Connection, table: String): List<IndexInfo> =
        readIndexRows(con, table).map { (name, rows) ->
            IndexInfo(
                name = name,
                unique = rows.first().unique,
                columns = rows.map { r ->
                    r.expression?.let { "($it)" } ?: (r.column + (r.subPart?.let { "($it)" } ?: "") + (if (r.desc) " DESC" else ""))
                },
                type = rows.first().indexType
            )
        }

    private data class IndexRow(
        val column: String?, val expression: String?, val subPart: Int?, val desc: Boolean,
        val unique: Boolean, val indexType: String, val comment: String?, val visible: Boolean?
    )

    private fun readIndexRows(con: Connection, table: String): Map<String, List<IndexRow>> {
        val grouped = LinkedHashMap<String, MutableList<Pair<Int, IndexRow>>>()
        con.createStatement().use { st ->
            st.executeQuery("SHOW INDEX FROM ${SqlNames.quote(table)}").use { rs ->
                val hasExpression = hasColumn(rs, "Expression")
                val hasVisible = hasColumn(rs, "Visible")
                while (rs.next()) {
                    val row = IndexRow(
                        column = rs.getString("Column_name"),
                        expression = if (hasExpression) rs.getString("Expression") else null,
                        subPart = rs.getObject("Sub_part")?.toString()?.toIntOrNull(),
                        desc = rs.getString("Collation") == "D",
                        unique = rs.getInt("Non_unique") == 0,
                        indexType = rs.getString("Index_type"),
                        comment = rs.getString("Index_comment"),
                        visible = if (hasVisible) rs.getString("Visible") == "YES" else null
                    )
                    grouped.getOrPut(rs.getString("Key_name")) { mutableListOf() }
                        .add(rs.getInt("Seq_in_index") to row)
                }
            }
        }
        return grouped.mapValues { (_, rows) -> rows.sortedBy { it.first }.map { it.second } }
    }

    private fun hasColumn(rs: ResultSet, label: String): Boolean {
        val md = rs.metaData
        return (1..md.columnCount).any { md.getColumnLabel(it).equals(label, ignoreCase = true) }
    }

    private companion object {
        val AI_COLUMN = Regex("^\\s+`((?:[^`]|``)+)`\\s.*\\bAUTO_INCREMENT\\b", RegexOption.MULTILINE)
        val AI_VALUE = Regex("\\bAUTO_INCREMENT=(\\d+)")
    }
}
