package com.example.mybeaver.dto

data class SessionInfo(
    val id: String,
    val name: String,
    val databases: List<String?>
)
