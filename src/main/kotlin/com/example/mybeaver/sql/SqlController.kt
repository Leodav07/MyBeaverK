package com.example.mybeaver.sql

import com.example.mybeaver.dto.print.ResultPrint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/connections/{connectionId}/sql")
class SqlController(private val service: SqlService) {

    @PostMapping
    fun execute(
        @PathVariable("connectionId") connectionId: String,
        @RequestBody request: SqlRequest
    ): ResponseEntity<ResultPrint<ScriptResult>> =
        ResponseEntity.ok(ResultPrint.success(service.execute(connectionId, request)))
}
