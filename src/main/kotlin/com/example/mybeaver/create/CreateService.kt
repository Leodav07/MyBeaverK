package com.example.mybeaver.create

import com.example.mybeaver.exceptions.InvalidConnectionDataException
import com.example.mybeaver.jdbc.ConnectionProvider
import com.example.mybeaver.jdbc.SqlNames
import org.springframework.stereotype.Service

@Service
class CreateService(private val provider: ConnectionProvider) {

    fun createTable(connectionId: String, database: String, request: CreateTableRequest): CreateResult {
        val sql = buildCreateTable(request)
        if (request.execute) run(connectionId, database, sql)
        return CreateResult("$sql;", request.execute)
    }

    fun createView(connectionId: String, database: String, request: CreateViewRequest): CreateResult {
        val sql = buildCreateView(request)
        if (request.execute) run(connectionId, database, sql)
        return CreateResult("$sql;", request.execute)
    }

    private fun run(connectionId: String, database: String, sql: String) {
        provider.open(connectionId, database).use { con ->
            con.createStatement().use { st -> st.execute(sql) }
        }
    }

    fun buildCreateTable(request: CreateTableRequest): String {
        val table = request.name.trim()
        if (table.isEmpty()) throw InvalidConnectionDataException("Escribe el nombre de la tabla.")
        if (request.columns.isEmpty()) throw InvalidConnectionDataException("La tabla necesita al menos una columna.")

        val names = request.columns.map { it.name.trim() }
        if (names.any { it.isEmpty() }) throw InvalidConnectionDataException("Hay columnas sin nombre.")
        names.groupBy { it.lowercase() }.values.firstOrNull { it.size > 1 }?.let {
            throw InvalidConnectionDataException("La columna \"${it.first()}\" está repetida.")
        }
        if (request.columns.count { it.autoIncrement } > 1) {
            throw InvalidConnectionDataException("Solo puede haber una columna AUTO_INCREMENT por tabla.")
        }

        val lines = request.columns.map { columnSql(it) }.toMutableList()

        val pk = request.columns.filter { it.primaryKey }
        if (pk.isNotEmpty()) lines += "PRIMARY KEY (" + pk.joinToString(", ") { SqlNames.quote(it.name.trim()) } + ")"
        request.columns.filter { it.unique && !it.primaryKey }.forEach {
            lines += "UNIQUE (" + SqlNames.quote(it.name.trim()) + ")"
        }

        val engine = request.engine?.trim().takeUnless { it.isNullOrEmpty() } ?: "InnoDB"
        val realEngine = ENGINES.firstOrNull { it.equals(engine, ignoreCase = true) }
            ?: throw InvalidConnectionDataException("Motor no soportado: $engine")

        val options = StringBuilder(") ENGINE=$realEngine")
        request.comment?.trim()?.takeIf { it.isNotEmpty() }?.let { options.append(" COMMENT=").append(SqlNames.literal(it)) }

        return "CREATE TABLE ${SqlNames.quote(table)} (\n" +
            lines.joinToString(",\n") { "  $it" } + "\n" + options
    }

    private fun columnSql(c: ColumnDefinition): String {
        val type = c.type.trim().uppercase()
        val rule = TYPES[type] ?: throw InvalidConnectionDataException("Tipo de dato no soportado: ${c.type}")
        val length = c.length?.trim().orEmpty()

        val typeSql = when (rule) {
            Length.NONE -> {
                if (length.isNotEmpty()) throw InvalidConnectionDataException("El tipo $type no lleva longitud (columna ${c.name}).")
                type
            }
            Length.OPTIONAL, Length.REQUIRED -> {
                if (length.isEmpty()) {
                    if (rule == Length.REQUIRED) throw InvalidConnectionDataException("El tipo $type necesita longitud (columna ${c.name}).")
                    type
                } else {
                    if (!Regex("\\d{1,5}").matches(length)) throw InvalidConnectionDataException("Longitud inválida \"$length\" en ${c.name}.")
                    "$type($length)"
                }
            }
            Length.PRECISION -> {
                if (length.isEmpty()) type
                else {
                    if (!Regex("\\d{1,2}(\\s*,\\s*\\d{1,2})?").matches(length)) {
                        throw InvalidConnectionDataException("Precisión inválida \"$length\" en ${c.name} (usa p.ej. 10,2).")
                    }
                    "$type(${length.replace(" ", "")})"
                }
            }
            Length.VALUES -> {
                val values = length.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                if (values.isEmpty()) throw InvalidConnectionDataException("$type necesita valores separados por coma (columna ${c.name}).")
                "$type(" + values.joinToString(",") { SqlNames.literal(it) } + ")"
            }
        }

        if (c.autoIncrement && type !in INTEGER_TYPES) {
            throw InvalidConnectionDataException("AUTO_INCREMENT solo se permite en columnas enteras (${c.name}).")
        }
        if (c.autoIncrement && !c.primaryKey && !c.unique) {
            throw InvalidConnectionDataException("La columna AUTO_INCREMENT ${c.name} debe ser PK o UNIQUE.")
        }

        val sb = StringBuilder(SqlNames.quote(c.name.trim())).append(' ').append(typeSql)
        sb.append(if (c.nullable && !c.primaryKey) " NULL" else " NOT NULL")
        defaultSql(type, c.defaultValue)?.let { sb.append(" DEFAULT ").append(it) }
        if (c.autoIncrement) sb.append(" AUTO_INCREMENT")
        c.comment?.trim()?.takeIf { it.isNotEmpty() }?.let { sb.append(" COMMENT ").append(SqlNames.literal(it)) }
        return sb.toString()
    }

    private fun defaultSql(type: String, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        if (value.equals("NULL", ignoreCase = true)) return "NULL"
        if (type in TEMPORAL_TYPES && CURRENT_TS.matches(value)) return value.uppercase()
        if ((type in INTEGER_TYPES || type in DECIMAL_TYPES) && NUMBER.matches(value)) return value
        return SqlNames.literal(value)
    }

    fun buildCreateView(request: CreateViewRequest): String {
        val name = request.name.trim()
        if (name.isEmpty()) throw InvalidConnectionDataException("Escribe el nombre de la vista.")
        val select = request.select.trim().trimEnd(';').trim()
        if (select.isEmpty()) throw InvalidConnectionDataException("Escribe la consulta SELECT de la vista.")
        if (!Regex("^(SELECT|WITH|\\()", RegexOption.IGNORE_CASE).containsMatchIn(select)) {
            throw InvalidConnectionDataException("La definición de una vista debe empezar con SELECT (o WITH).")
        }
        val orReplace = if (request.orReplace) "OR REPLACE " else ""
        return "CREATE ${orReplace}VIEW ${SqlNames.quote(name)} AS\n$select"
    }

    private enum class Length { NONE, OPTIONAL, REQUIRED, PRECISION, VALUES }

    private companion object {
        val INTEGER_TYPES = setOf("TINYINT", "SMALLINT", "MEDIUMINT", "INT", "BIGINT")
        val DECIMAL_TYPES = setOf("DECIMAL", "FLOAT", "DOUBLE")
        val TEMPORAL_TYPES = setOf("DATETIME", "TIMESTAMP")

        val TYPES: Map<String, Length> = buildMap {
            INTEGER_TYPES.forEach { put(it, Length.NONE) }
            put("DECIMAL", Length.PRECISION)
            put("FLOAT", Length.NONE); put("DOUBLE", Length.NONE)
            put("BOOLEAN", Length.NONE)
            put("CHAR", Length.OPTIONAL); put("VARCHAR", Length.REQUIRED)
            put("BINARY", Length.OPTIONAL); put("VARBINARY", Length.REQUIRED)
            listOf("TINYTEXT", "TEXT", "MEDIUMTEXT", "LONGTEXT", "BLOB", "MEDIUMBLOB", "LONGBLOB", "JSON", "DATE", "YEAR")
                .forEach { put(it, Length.NONE) }
            put("DATETIME", Length.OPTIONAL); put("TIMESTAMP", Length.OPTIONAL); put("TIME", Length.OPTIONAL)
            put("ENUM", Length.VALUES); put("SET", Length.VALUES)
        }

        val ENGINES = listOf("InnoDB", "MyISAM", "MEMORY", "CSV", "ARCHIVE")
        val NUMBER = Regex("-?\\d+(\\.\\d+)?")
        val CURRENT_TS = Regex("CURRENT_TIMESTAMP(\\(\\d?\\))?", RegexOption.IGNORE_CASE)
    }
}
