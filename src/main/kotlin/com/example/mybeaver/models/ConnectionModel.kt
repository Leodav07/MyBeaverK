package com.example.mybeaver.models

data class ConnectionModel (
    var id : String,
    var name : String,
    var host: String,
    var port: Int = 0,
    var username: String,
    var password: String
)
