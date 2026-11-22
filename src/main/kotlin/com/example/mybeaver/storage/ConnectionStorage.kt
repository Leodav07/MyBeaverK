package com.example.mybeaver.storage

import com.example.mybeaver.models.ConnectionModel
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.core.JacksonException
import tools.jackson.module.kotlin.jacksonObjectMapper
import tools.jackson.module.kotlin.readValue
import java.io.File

@Component
class ConnectionStorage(
    @Value("\${app.connections-file:data/connections.json}") filePath: String
) {

    private val mapper = jacksonObjectMapper()
    private val file = File(filePath)

    @Synchronized
    fun loadConnections(): MutableList<ConnectionModel> {
        if (!file.exists() || file.length() == 0L) {
            return mutableListOf()
        }
        try {
            return mapper.readValue(file)
        } catch (e: JacksonException) {
            throw IllegalStateException("El archivo de conexiones está dañado: ${file.absolutePath}", e)
        }
    }

    @Synchronized
    fun addConnection(newConnection: ConnectionModel): Boolean {
        val currentList = loadConnections()
        if (currentList.any { it.id == newConnection.id }) return false

        currentList.add(newConnection)
        save(currentList)
        return true
    }

    @Synchronized
    fun removeConnection(id: String): Boolean {
        val currentList = loadConnections()
        val removed = currentList.removeIf { it.id == id }
        if (removed) save(currentList)
        return removed
    }

    @Synchronized
    fun searchConnection(id: String): ConnectionModel? =
        loadConnections().find { it.id == id }

    private fun save(list: List<ConnectionModel>) {
        file.parentFile?.mkdirs()
        mapper.writerWithDefaultPrettyPrinter().writeValue(file, list)
    }
}