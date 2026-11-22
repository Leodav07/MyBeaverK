package com.example.mybeaver.controllers

import com.example.mybeaver.dto.ConnectionSummary
import com.example.mybeaver.dto.SaveConnectionRequest
import com.example.mybeaver.dto.print.ResultPrint
import com.example.mybeaver.services.SavedConnectionService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/connections")
class SavedConnectionController(private val service: SavedConnectionService) {

    @GetMapping
    fun list(): ResponseEntity<ResultPrint<List<ConnectionSummary>>> =
        ResponseEntity.ok(ResultPrint.success(service.list()))

    @PostMapping
    fun save(@RequestBody request: SaveConnectionRequest): ResponseEntity<ResultPrint<ConnectionSummary>> =
        ResponseEntity.status(HttpStatus.CREATED).body(ResultPrint.success(service.save(request)))

    @DeleteMapping("/{id}")
    fun delete(@PathVariable("id") id: String): ResponseEntity<Void> {
        service.delete(id)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{id}/connect")
    fun connect(@PathVariable("id") id: String): ResponseEntity<ResultPrint<MutableList<String?>>> =
        ResponseEntity.ok(ResultPrint.success(service.connect(id)))
}