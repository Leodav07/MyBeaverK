package com.example.mybeaver.jdbc

import com.example.mybeaver.exceptions.ConnectionNotFoundException
import com.example.mybeaver.security.PasswordCipher
import com.example.mybeaver.storage.ConnectionStorage
import com.example.mybeaver.storage.SessionConnections
import org.springframework.stereotype.Component
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

@Component
class ConnectionProvider(
    private val storage: ConnectionStorage,
    private val sessions: SessionConnections,
    private val cipher: PasswordCipher
) {

    @Throws(SQLException::class)
    fun open(connectionId: String, database: String? = null, allowMultiQueries: Boolean = false): Connection {
        val saved = storage.searchConnection(connectionId)
            ?: sessions.find(connectionId)
            ?: throw ConnectionNotFoundException(connectionId)
        val password = cipher.decrypt(saved.password)

        val options = mutableListOf("connectTimeout=5000")
        if (allowMultiQueries) options.add("allowMultiQueries=true")
        val url = "jdbc:mysql://${saved.host}:${saved.port}?" + options.joinToString("&")

        val connection = DriverManager.getConnection(url, saved.username, password)

        if (!database.isNullOrBlank()) {
            try {
                connection.catalog = database
            } catch (e: SQLException) {
                connection.close()
                throw e
            }
        }
        return connection
    }
}
