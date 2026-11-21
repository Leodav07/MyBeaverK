package com.example.mybeaver.services

import com.example.mybeaver.dto.ConnectionInfodto
import java.sql.SQLException

interface ConnectionInterface {
    @Throws(SQLException::class)
    fun connection(connecDto: ConnectionInfodto?): MutableList<String?>?

}