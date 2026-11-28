package com.example.mybeaver.storage

import com.example.mybeaver.dto.ConnectionInfodto
import com.example.mybeaver.models.ConnectionModel
import com.example.mybeaver.security.PasswordCipher
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Component
class SessionConnections(private val cipher: PasswordCipher) {

    private val sessions = ConcurrentHashMap<String, ConnectionModel>()

    fun add(dto: ConnectionInfodto): ConnectionModel {
        val model = ConnectionModel(
            id = "tmp-" + UUID.randomUUID(),
            name = "${dto.username}@${dto.host}:${dto.port}",
            host = dto.host.trim(),
            port = dto.port,
            username = dto.username.trim(),
            password = cipher.encrypt(dto.password)
        )
        sessions[model.id] = model
        return model
    }

    fun find(id: String): ConnectionModel? = sessions[id]

    fun remove(id: String): Boolean = sessions.remove(id) != null
}
