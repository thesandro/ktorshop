package com.highsteaks.models

import kotlinx.serialization.Serializable

@Serializable
data class Message(
    val user:String,
    val isMine:Boolean,
    val text:String
)
