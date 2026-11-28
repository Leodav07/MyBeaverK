package com.example.mybeaver.metadata

import com.example.mybeaver.exceptions.InvalidConnectionDataException

enum class ObjectScope { DATABASE, SERVER }

enum class ObjectType(
    val urlName: String,
    val label: String,
    val scope: ObjectScope = ObjectScope.DATABASE,
    val supported: Boolean = true,
    val note: String? = null
) {
    TABLE("tables", "Tablas"),
    VIEW("views", "Vistas"),
    PROCEDURE("procedures", "Procedimientos"),
    FUNCTION("functions", "Funciones"),
    TRIGGER("triggers", "Triggers"),
    EVENT("events", "Eventos", note = "Tareas programadas de MySQL (SHOW EVENTS)."),
    INDEX("indexes", "Índices", note = "Se leen tabla por tabla con SHOW INDEX."),
    SEQUENCE(
        "sequences", "Secuencias",
        note = "MySQL no tiene CREATE SEQUENCE. Su generador equivalente es la columna AUTO_INCREMENT; " +
            "aquí se listan esas columnas y su siguiente valor (leído de SHOW CREATE TABLE)."
    ),
    PACKAGE(
        "packages", "Paquetes",
        supported = false,
        note = "MySQL no implementa paquetes (CREATE PACKAGE es propio de Oracle PL/SQL). " +
            "La forma de agrupar lógica en MySQL es poner los procedimientos y funciones relacionados " +
            "dentro de la misma base de datos (esquema)."
    ),
    TABLESPACE(
        "tablespaces", "Tablespaces",
        scope = ObjectScope.SERVER,
        note = "Objetos de todo el servidor. Se leen de la tabla de sistema performance_schema.file_instances."
    ),
    USER(
        "users", "Usuarios",
        scope = ObjectScope.SERVER,
        note = "Objetos de todo el servidor. Se leen de la tabla de sistema mysql.user."
    );

    fun toInfo() = ObjectTypeInfo(urlName, label, scope.name.lowercase(), supported, note)

    companion object {
        fun fromUrl(value: String): ObjectType =
            entries.firstOrNull { it.urlName == value }
                ?: throw InvalidConnectionDataException("Tipo de objeto desconocido: $value")
    }
}

data class ObjectTypeInfo(
    val urlName: String,
    val label: String,
    val scope: String,
    val supported: Boolean,
    val note: String?
)

data class DbObject(
    val name: String,
    val type: String,
    val detail: String? = null,
    val parent: String? = null
)

data class ColumnInfo(
    val name: String,
    val type: String,
    val nullable: Boolean,
    val key: String,
    val default: String?,
    val extra: String,
    val comment: String
)

data class IndexInfo(val name: String, val unique: Boolean, val columns: List<String>, val type: String)
