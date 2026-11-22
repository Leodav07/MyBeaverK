package com.example.mybeaver.dto

data class SaveConnectionRequest(
    val name: String,
    val host: String,
    val port: Int,
    val username: String,
    val password: String
)