package com.example.mybeaver.dto.print

data class ResultPrint<out T : Any> private constructor(
    val ok: Boolean,
    val msg: String?,
    val data: T?
) {
    companion object {
        fun <T : Any> success(data: T?, msg: String = ""): ResultPrint<T> =
            ResultPrint(true, msg, data)

        fun fail(msg: String?): ResultPrint<Nothing> =
            ResultPrint(false, msg, null)
    }
}