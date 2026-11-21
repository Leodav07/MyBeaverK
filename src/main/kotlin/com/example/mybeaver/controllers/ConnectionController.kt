package com.example.mybeaver.controllers

import com.example.mybeaver.dto.ConnectionInfodto
import com.example.mybeaver.dto.print.ResultPrint
import com.example.mybeaver.services.ConnectionInterface
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.*
import java.sql.SQLException


@RestController
@RequestMapping("/api/")
class ConnectionController(private val connectionService: ConnectionInterface) {


    @PostMapping("conn/connect")
    fun connect(@RequestBody connectionDto: ConnectionInfodto?): ResponseEntity<ResultPrint<MutableList<String?>>> {
        return try {
            val dataBaseList: MutableList<String?>? = connectionService.connection(connectionDto)

            ResponseEntity.ok(ResultPrint.success(dataBaseList))
        } catch (sq: SQLException) {
            val code = sq.errorCode
            println(code.toString() + sq.message)
            when (code) {
                0 -> ResponseEntity.status(502).body(ResultPrint.fail(
                            "Comunicacion con el servidor fallida. " +
                                    "Posibles Causas: Servidor apagado, Puerto Incorrecto, Direccion Incorrecta."
                        )
                    )


                1045 -> ResponseEntity.status(401).body(ResultPrint.fail("Acceso denegado: Credenciales invalidas."))


                else -> ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ResultPrint.fail(sq.message))

            }
        }
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun jsonInvalido(e: HttpMessageNotReadableException?): ResponseEntity<ResultPrint<MutableList<String?>>> {
        return ResponseEntity.badRequest().body(ResultPrint.fail(
            "Comunicacion con el servidor fallida. " +
                    "Posibles Causas: Servidor apagado, Puerto Incorrecto, Direccion Incorrecta."
        )
        )
    }
}