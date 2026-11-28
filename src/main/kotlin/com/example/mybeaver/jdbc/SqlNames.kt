package com.example.mybeaver.jdbc

import com.example.mybeaver.exceptions.InvalidConnectionDataException

object SqlNames {

    fun quote(name: String): String {
        if (name.isEmpty() || name.length > 64 || name.contains('\u0000')) {
            throw InvalidConnectionDataException("Nombre de objeto inválido.")
        }
        return "`" + name.replace("`", "``") + "`"
    }

    fun literal(value: String): String =
        "'" + value.replace("\\", "\\\\").replace("'", "''") + "'"
}
