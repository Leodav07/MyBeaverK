package com.example.mybeaver.exceptions

class InvalidConnectionDataException(message: String) : RuntimeException(message)

class DuplicateConnectionException(name: String) :
    RuntimeException("Ya existe una conexión guardada con el nombre \"$name\".")

class ConnectionNotFoundException(id: String) :
    RuntimeException("No existe una conexión guardada con id $id.")

class ObjectNotFoundException(name: String) :
    RuntimeException("No existe el objeto \"$name\" en esa base de datos.")

class NotApplicableException(message: String) : RuntimeException(message)

class PermissionDeniedException(message: String) : RuntimeException(message)
