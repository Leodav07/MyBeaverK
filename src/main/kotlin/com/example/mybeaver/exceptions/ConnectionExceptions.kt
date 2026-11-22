package com.example.mybeaver.exceptions

class InvalidConnectionDataException(message: String) : RuntimeException(message)

class DuplicateConnectionException(name: String) :
    RuntimeException("Ya existe una conexión guardada con el nombre \"$name\".")

class ConnectionNotFoundException(id: String) :
    RuntimeException("No existe una conexión guardada con id $id.")