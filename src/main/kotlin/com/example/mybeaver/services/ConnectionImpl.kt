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
        println("Entró al método")
        val url = "jdbc:mysql://" + connecDto?.host + ":" + connecDto?.port + "?connectTimeout=5000"

        DriverManager.getConnection(url, connecDto?.username, connecDto?.password).use { con ->
            val stmt = con.createStatement()
            val rs = stmt.executeQuery("SHOW DATABASES;")

            while (rs.next()) {
                val dbName = rs.getString("Database")
                dataBaseList.add(dbName)
            }
            return dataBaseList
        }
    }
}