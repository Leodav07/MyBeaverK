package com.example.mybeaver.sql

data class SqlRequest(
    val sql: String,
    val database: String? = null,
    val maxRows: Int = 500
)

enum class ResultKind { RESULT_SET, UPDATE, ERROR }

data class StatementResult(
    val index: Int,
    val sql: String,
    val kind: ResultKind,
    val columns: List<String> = emptyList(),
    val rows: List<List<String?>> = emptyList(),
    val truncated: Boolean = false,
    val updateCount: Long? = null,
    val message: String? = null,
    val elapsedMs: Long = 0
)

data class ScriptResult(
    val results: List<StatementResult>,
    val failed: Boolean,
    val totalMs: Long
)
