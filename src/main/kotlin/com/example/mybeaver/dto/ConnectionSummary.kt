package com.example.mybeaver.dto

data class ConnectionSummary(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val username: String
)
