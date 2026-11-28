package com.example.mybeaver.services

import com.example.mybeaver.dto.ConnectionInfodto
import com.example.mybeaver.dto.ConnectionSummary
import com.example.mybeaver.dto.SaveConnectionRequest
import com.example.mybeaver.dto.SessionInfo
import java.sql.SQLException

interface SavedConnectionService {

    fun list(): List<ConnectionSummary>

    fun save(request: SaveConnectionRequest): ConnectionSummary
    fun delete(id: String)

    @Throws(SQLException::class)
    fun connect(id: String): MutableList<String?>?

    @Throws(SQLException::class)
    fun openSession(dto: ConnectionInfodto): SessionInfo

    fun closeSession(id: String)
}
