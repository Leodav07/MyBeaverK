package com.example.mybeaver.create

import com.example.mybeaver.dto.print.ResultPrint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/connections/{connectionId}/databases/{database}/create")
class CreateController(private val service: CreateService) {

    @PostMapping("/table")
    fun table(
        @PathVariable("connectionId") connectionId: String,
        @PathVariable("database") database: String,
        @RequestBody request: CreateTableRequest
    ): ResponseEntity<ResultPrint<CreateResult>> =
        ResponseEntity.ok(ResultPrint.success(service.createTable(connectionId, database, request)))

    @PostMapping("/view")
    fun view(
        @PathVariable("connectionId") connectionId: String,
        @PathVariable("database") database: String,
        @RequestBody request: CreateViewRequest
    ): ResponseEntity<ResultPrint<CreateResult>> =
        ResponseEntity.ok(ResultPrint.success(service.createView(connectionId, database, request)))
}
