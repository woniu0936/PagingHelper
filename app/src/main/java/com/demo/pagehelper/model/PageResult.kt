package com.demo.pagehelper.model

data class PageResult<Key, T>(
    val data: List<T>,
    val nextKey: Key?
)
