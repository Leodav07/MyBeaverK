package com.example.mybeaver.create

data class ColumnDefinition(
    val name: String,
    val type: String,
    val length: String? = null,
    val nullable: Boolean = true,
    val primaryKey: Boolean = false,
    val autoIncrement: Boolean = false,
    val unique: Boolean = false,
    val defaultValue: String? = null,
    val comment: String? = null
)

data class CreateTableRequest(
    val name: String,
    val columns: List<ColumnDefinition>,
    val engine: String? = "InnoDB",
    val comment: String? = null,
    val execute: Boolean = false
)

data class CreateViewRequest(
    val name: String,
    val select: String,
    val orReplace: Boolean = false,
    val execute: Boolean = false
)

data class CreateResult(val sql: String, val executed: Boolean)
