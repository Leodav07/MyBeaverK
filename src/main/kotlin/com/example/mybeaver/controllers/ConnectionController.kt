package com.example.mybeaver.controllers

import com.example.mybeaver.dto.ConnectionInfodto
import com.example.mybeaver.dto.print.ResultPrint
import com.example.mybeaver.exceptions.InvalidConnectionDataException
import com.example.mybeaver.services.ConnectionInterface
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/conn")
class ConnectionController(private val connectionService: ConnectionInterface) {

    // Los errores (SQLException, datos inválidos...) los traduce GlobalExceptionHandler
    @PostMapping("/connect")
    fun connect(@RequestBody dto: ConnectionInfodto): ResponseEntity<ResultPrint<MutableList<String?>>> {
        validate(dto)
        return ResponseEntity.ok(ResultPrint.success(connectionService.connection(dto)))
    }

    private fun validate(dto: ConnectionInfodto) {
        if (dto.host.isBlank()) throw InvalidConnectionDataException("El servidor es obligatorio.")
        if (dto.port !in 1..65535) throw InvalidConnectionDataException("El puerto debe estar entre 1 y 65535.")
        if (dto.username.isBlank()) throw InvalidConnectionDataException("El usuario es obligatorio.")
    }
}