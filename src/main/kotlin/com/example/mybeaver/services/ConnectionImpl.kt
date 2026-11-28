package com.example.mybeaver.services

import com.example.mybeaver.dto.ConnectionInfodto
import org.springframework.stereotype.Service
import java.sql.DriverManager
import java.sql.SQLException

@Service
class ConnectionImpl : ConnectionInterface {
    @Throws(SQLException::class)

    override fun connection(connecDto: ConnectionInfodto?): MutableList<String?>? {
        val dataBaseList: MutableList<String?> = ArrayList<String?>()
        val url = "jdbc:mysql://" + connecDto?.host + ":" + connecDto?.port + "?connectTimeout=5000"

        DriverManager.getConnection(url, connecDto?.username, connecDto?.password).use { con ->
            con.createStatement().use { stmt ->
                stmt.executeQuery("SHOW DATABASES").use { rs ->
                    while (rs.next()) dataBaseList.add(rs.getString("Database"))
                }
            }
            return dataBaseList
        }
    }
}