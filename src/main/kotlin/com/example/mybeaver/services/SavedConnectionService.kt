package com.example.mybeaver.services

import com.example.mybeaver.dto.ConnectionSummary
import com.example.mybeaver.dto.SaveConnectionRequest
import java.sql.SQLException

interface SavedConnectionService {

    fun list(): List<ConnectionSummary>

    fun save(request: SaveConnectionRequest): ConnectionSummary
    fun delete(id: String)

    @Throws(SQLException::class)
    fun connect(id: String): MutableList<String?>?
}