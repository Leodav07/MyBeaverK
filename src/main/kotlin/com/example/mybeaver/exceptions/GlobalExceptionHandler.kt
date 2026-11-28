package com.example.mybeaver.exceptions

import com.example.mybeaver.dto.print.ResultPrint
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.sql.SQLException

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidConnectionDataException::class)
    fun invalidData(e: InvalidConnectionDataException) = error(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadableBody(e: HttpMessageNotReadableException) =
        error(HttpStatus.BAD_REQUEST, "Uno o varios datos enviados no son válidos. Revisa los campos e inténtalo de nuevo.")

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun missingParam(e: MissingServletRequestParameterException) =
        error(HttpStatus.BAD_REQUEST, "Falta el parámetro \"${e.parameterName}\".")

    @ExceptionHandler(DuplicateConnectionException::class)
    fun duplicate(e: DuplicateConnectionException) = error(HttpStatus.CONFLICT, e.message)

    @ExceptionHandler(ConnectionNotFoundException::class)
    fun notFound(e: ConnectionNotFoundException) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(ObjectNotFoundException::class)
    fun objectNotFound(e: ObjectNotFoundException) = error(HttpStatus.NOT_FOUND, e.message)

    @ExceptionHandler(NotApplicableException::class)
    fun notApplicable(e: NotApplicableException) = error(HttpStatus.BAD_REQUEST, e.message)

    @ExceptionHandler(PermissionDeniedException::class)
    fun forbidden(e: PermissionDeniedException) = error(HttpStatus.FORBIDDEN, e.message)

    @ExceptionHandler(SQLException::class)
    fun sql(e: SQLException): ResponseEntity<ResultPrint<Nothing>> {
        log.warn("Fallo de MySQL: código={} sqlState={} mensaje={}", e.errorCode, e.sqlState, e.message)

        val communicationFailure = e.sqlState?.startsWith("08") == true ||
            (e.errorCode == 0 && e.sqlState == null)
        return when {
            communicationFailure -> error(
                HttpStatus.BAD_GATEWAY,
                "Comunicación con el servidor fallida. Posibles causas: servidor apagado, puerto incorrecto o dirección incorrecta."
            )
            e.errorCode == 1045 -> error(HttpStatus.UNAUTHORIZED, "Acceso denegado: credenciales inválidas.")
            e.errorCode in PRIVILEGE_ERRORS -> error(
                HttpStatus.FORBIDDEN,
                "Tu usuario no tiene privilegios para esta operación. MySQL dice: ${e.message}"
            )
            e.errorCode in NOT_FOUND_ERRORS -> error(HttpStatus.NOT_FOUND, e.message)
            else -> error(HttpStatus.BAD_REQUEST, e.message)
        }
    }

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

    private companion object {
        val PRIVILEGE_ERRORS = setOf(1044, 1142, 1143, 1227, 1370)
        val NOT_FOUND_ERRORS = setOf(1049, 1146, 1305, 1360, 1539, 1176)
    }
}
