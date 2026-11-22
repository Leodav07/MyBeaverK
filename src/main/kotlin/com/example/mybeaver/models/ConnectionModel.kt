package com.example.mybeaver.models

data class ConnectionModel (
    var id : Int = 0,
    var name : String? = null,
    var host: String? = null,
    var port: Int = 0,
    var username: String? = null,
    var password: String? = null
)
