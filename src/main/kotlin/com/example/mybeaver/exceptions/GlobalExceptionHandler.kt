package com.example.mybeaver.exceptions

import com.example.mybeaver.dto.print.ResultPrint
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.sql.SQLException

// Atiende las excepciones de TODOS los controladores y siempre responde { ok:false, msg, data:null }
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidConnectionDataException::class)
    fun invalidData(e: InvalidConnectionDataException) = error(HttpStatus.BAD_REQUEST, e.message)

    // Se dispara antes de entrar al método del controlador: JSON mal formado o campos que faltan
    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(e: HttpMessageNotReadableException) =
        error(HttpStatus.BAD_REQUEST, "Uno o varios datos enviados no son válidos. Revisa los campos e inténtalo de nuevo.")

    @ExceptionHandler(DuplicateConnectionException::class)
    fun duplicate(e: DuplicateConnectionException) = error(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(ConnectionNotFoundException::class)
    fun notFound(e: ConnectionNotFoundException) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(SQLException::class)
    fun sql(e: SQLException): ResponseEntity<ResultPrint<Nothing>> {
        log.warn("Fallo de MySQL: código={} sqlState={} mensaje={}", e.errorCode, e.sqlState, e.message)

        val communicationFailure = e.errorCode == 0 || e.sqlState?.startsWith("08") == true
        return when {
            communicationFailure -> error(
                HttpStatus.BAD_GATEWAY,
                "Comunicación con el servidor fallida. Posibles causas: servidor apagado, puerto incorrecto o dirección incorrecta."
            )
            e.errorCode == 1045 -> error(HttpStatus.UNAUTHORIZED, "Acceso denegado: credenciales inválidas.")
            else -> error(HttpStatus.BAD_REQUEST, e.message)
        }
    }

    // Archivo de conexiones dañado o clave que no se puede descifrar.
    // El detalle (puede incluir rutas del servidor) va al log, no al cliente.
    @ExceptionHandler(IllegalStateException::class)
    fun internal(e: IllegalStateException): ResponseEntity<ResultPrint<Nothing>> {
        log.error("Error interno: {}", e.message, e)
        return error(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Error interno: no se pudo leer o descifrar la información guardada. Revisa el registro del servidor."
        )
    }

    private fun error(status: HttpStatus, msg: String?): ResponseEntity<ResultPrint<Nothing>> =
        ResponseEntity.status(status).body(ResultPrint.fail(msg))
}