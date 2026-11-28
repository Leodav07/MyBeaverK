package com.example.mybeaver.metadata

import com.example.mybeaver.dto.print.ResultPrint
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


@RestController
@RequestMapping("/api")
class MetadataController(private val service: MetadataService) {

    @GetMapping("/object-types")
    fun types(): ResponseEntity<ResultPrint<List<ObjectTypeInfo>>> =
        ResponseEntity.ok(ResultPrint.success(ObjectType.entries.map { it.toInfo() }))

    @GetMapping("/connections/{connectionId}/databases")
    fun databases(@PathVariable("connectionId") connectionId: String): ResponseEntity<ResultPrint<List<String>>> =
        ResponseEntity.ok(ResultPrint.success(service.listDatabases(connectionId)))

    @GetMapping("/connections/{connectionId}/databases/{database}/objects/{typeName}")
    fun list(
        @PathVariable("connectionId") connectionId: String,
        @PathVariable("database") database: String,
        @PathVariable("typeName") typeName: String
    ): ResponseEntity<ResultPrint<List<DbObject>>> =
        ResponseEntity.ok(
            ResultPrint.success(service.listObjects(connectionId, database, ObjectType.fromUrl(typeName)))
        )

    @GetMapping("/connections/{connectionId}/databases/{database}/objects/{typeName}/ddl")
    fun ddl(
        @PathVariable("connectionId") connectionId: String,
        @PathVariable("database") database: String,
        @PathVariable("typeName") typeName: String,
        @RequestParam("name") name: String,
        @RequestParam("table", required = false) table: String?
    ): ResponseEntity<ResultPrint<String>> =
        ResponseEntity.ok(
            ResultPrint.success(service.ddl(connectionId, database, ObjectType.fromUrl(typeName), name, table))
        )

    @GetMapping("/connections/{connectionId}/databases/{database}/columns")
    fun columns(
        @PathVariable("connectionId") connectionId: String,
        @PathVariable("database") database: String,
        @RequestParam("table") table: String
    ): ResponseEntity<ResultPrint<List<ColumnInfo>>> =
        ResponseEntity.ok(ResultPrint.success(service.listColumns(connectionId, database, table)))

    @GetMapping("/connections/{connectionId}/databases/{database}/indexes")
    fun indexes(
        @PathVariable("connectionId") connectionId: String,
        @PathVariable("database") database: String,
        @RequestParam("table") table: String
    ): ResponseEntity<ResultPrint<List<IndexInfo>>> =
        ResponseEntity.ok(ResultPrint.success(service.listIndexes(connectionId, database, table)))
}
