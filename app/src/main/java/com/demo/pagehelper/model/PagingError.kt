package com.demo.pagehelper.model

import java.io.IOException

/**
 * 结构化的业务错误实体，用于精确的错误处理和监控。
 */
sealed class PagingError {
    data class Network(val cause: Throwable) : PagingError()
    data class Server(val code: Int, val message: String) : PagingError()
    data class Unknown(val cause: Throwable) : PagingError()
}

/**
 * 将底层异常映射为定义的业务错误的扩展函数。
 */
fun Throwable.toPagingError(): PagingError {
    return when (this) {
        is IOException -> PagingError.Network(this)
        // 在实际项目中，这里可以判断 Retrofit 的 HttpException 等更具体的网络错误类型
        // is HttpException -> PagingError.Server(this.code(), this.message())
        else -> PagingError.Unknown(this)
    }
}
