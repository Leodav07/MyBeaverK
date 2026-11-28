package com.example.mybeaver

import com.example.mybeaver.create.ColumnDefinition
import com.example.mybeaver.create.CreateService
import com.example.mybeaver.create.CreateTableRequest
import com.example.mybeaver.create.CreateViewRequest
import com.example.mybeaver.exceptions.InvalidConnectionDataException
import com.example.mybeaver.jdbc.ConnectionProvider
import com.example.mybeaver.security.PasswordCipher
import com.example.mybeaver.storage.ConnectionStorage
import com.example.mybeaver.storage.SessionConnections
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CreateServiceTest {

    private val cipher = PasswordCipher(Base64.getEncoder().encodeToString(ByteArray(32)))
    private val service = CreateService(
        ConnectionProvider(ConnectionStorage("target/test-connections.json"), SessionConnections(cipher), cipher)
    )

    @Test
    fun `arma un CREATE TABLE completo`() {
        val sql = service.buildCreateTable(
            CreateTableRequest(
                name = "clientes",
                columns = listOf(
                    ColumnDefinition("id", "int", primaryKey = true, autoIncrement = true),
                    ColumnDefinition("nombre", "VARCHAR", "100", nullable = false, comment = "Nombre's"),
                    ColumnDefinition("email", "varchar", "150", unique = true),
                    ColumnDefinition("saldo", "DECIMAL", "10,2", defaultValue = "0"),
                    ColumnDefinition("estado", "ENUM", "activo,inactivo", defaultValue = "activo"),
                    ColumnDefinition("creado", "DATETIME", defaultValue = "current_timestamp")
                ),
                comment = "Tabla de prueba"
            )
        )
        assertEquals(
            """
            CREATE TABLE `clientes` (
              `id` INT NOT NULL AUTO_INCREMENT,
              `nombre` VARCHAR(100) NOT NULL COMMENT 'Nombre''s',
              `email` VARCHAR(150) NULL,
              `saldo` DECIMAL(10,2) NULL DEFAULT 0,
              `estado` ENUM('activo','inactivo') NULL DEFAULT 'activo',
              `creado` DATETIME NULL DEFAULT CURRENT_TIMESTAMP,
              PRIMARY KEY (`id`),
              UNIQUE (`email`)
            ) ENGINE=InnoDB COMMENT='Tabla de prueba'
            """.trimIndent(),
            sql
        )
    }

    @Test
    fun `rechaza tipos que no estan en la lista blanca`() {
        assertFailsWith<InvalidConnectionDataException> {
            service.buildCreateTable(CreateTableRequest("t", listOf(ColumnDefinition("a", "INT); DROP TABLE x; --"))))
        }
    }

    @Test
    fun `cita nombres peligrosos`() {
        val sql = service.buildCreateTable(CreateTableRequest("a`b", listOf(ColumnDefinition("c`d", "INT"))))
        assertEquals("CREATE TABLE `a``b` (\n  `c``d` INT NULL\n) ENGINE=InnoDB", sql)
    }

    @Test
    fun `arma un CREATE VIEW`() {
        assertEquals(
            "CREATE OR REPLACE VIEW `v` AS\nSELECT 1",
            service.buildCreateView(CreateViewRequest("v", "SELECT 1;", orReplace = true))
        )
    }
}
