package com.example.mybeaver.services

import com.example.mybeaver.dto.ConnectionInfodto
import com.example.mybeaver.dto.ConnectionSummary
import com.example.mybeaver.dto.SaveConnectionRequest
import com.example.mybeaver.dto.SessionInfo
import com.example.mybeaver.exceptions.ConnectionNotFoundException
import com.example.mybeaver.exceptions.DuplicateConnectionException
import com.example.mybeaver.exceptions.InvalidConnectionDataException
import com.example.mybeaver.models.ConnectionModel
import com.example.mybeaver.security.PasswordCipher
import com.example.mybeaver.storage.ConnectionStorage
import com.example.mybeaver.storage.SessionConnections
import org.springframework.stereotype.Service
import java.sql.SQLException
import java.util.UUID

@Service
class SavedConnectionServiceImpl(
    private val storage: ConnectionStorage,
    private val cipher: PasswordCipher,
    private val connectionService: ConnectionInterface,
    private val sessions: SessionConnections
) : SavedConnectionService {

    override fun list(): List<ConnectionSummary> =
        storage.loadConnections().map { it.toSummary() }

    override fun save(request: SaveConnectionRequest): ConnectionSummary {
        val name = request.name.trim()
        val host = request.host.trim()
        val username = request.username.trim()
        validate(name, host, request.port, username)

        val nameTaken = storage.loadConnections().any { it.name.equals(name, ignoreCase = true) }
        if (nameTaken) throw DuplicateConnectionException(name)

        val model = ConnectionModel(
            id = UUID.randomUUID().toString(),
            name = name,
            host = host,
            port = request.port,
            username = username,
            password = cipher.encrypt(request.password)
        )
        storage.addConnection(model)
        return model.toSummary()
    }

    override fun delete(id: String) {
        if (!storage.removeConnection(id)) throw ConnectionNotFoundException(id)
    }

    @Throws(SQLException::class)
    override fun connect(id: String): MutableList<String?>? {
        val saved = storage.searchConnection(id) ?: throw ConnectionNotFoundException(id)
        val plainPassword = cipher.decrypt(saved.password)
        return connectionService.connection(
            ConnectionInfodto(saved.host, saved.port, saved.username, plainPassword)
        )
    }

    @Throws(SQLException::class)
    override fun openSession(dto: ConnectionInfodto): SessionInfo {
        validate("sesion", dto.host.trim(), dto.port, dto.username.trim())
        val databases = connectionService.connection(dto) ?: mutableListOf()
        val model = sessions.add(dto)
        return SessionInfo(model.id, model.name, databases)
    }

    override fun closeSession(id: String) {
        sessions.remove(id)
    }

    private fun validate(name: String, host: String, port: Int, username: String) {
        if (name.isEmpty()) throw InvalidConnectionDataException("El nombre es obligatorio.")
        if (host.isEmpty()) throw InvalidConnectionDataException("El servidor es obligatorio.")
        if (port !in 1..65535) throw InvalidConnectionDataException("El puerto debe estar entre 1 y 65535.")
        if (username.isEmpty()) throw InvalidConnectionDataException("El usuario es obligatorio.")
    }

    private fun ConnectionModel.toSummary() = ConnectionSummary(id, name, host, port, username)
}