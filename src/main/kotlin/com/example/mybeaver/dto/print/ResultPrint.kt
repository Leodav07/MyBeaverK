package com.example.mybeaver.dto.print

data class ResultPrint<out T : Any> private constructor(
     val ok: Boolean,
     val msg: String?,
     val data: T?)
{
    companion object {
        fun success(data: MutableList<String?>?): ResultPrint<MutableList<String?>> {
            return ResultPrint(true, "", data)
        }

        fun fail(msg: String?): ResultPrint<Nothing> {
            return ResultPrint(false, msg, null)
        }
    }
}
