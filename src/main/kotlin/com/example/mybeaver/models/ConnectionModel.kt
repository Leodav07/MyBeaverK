package com.example.mybeaver.models

data class ConnectionModel (
    var host: String? = null,
    var port: Int = 0,
    var username: String? = null,
    var password: String? = null
)
