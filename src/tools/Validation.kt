package com.highsteaks.tools

import io.ktor.http.*
import io.ktor.util.*

fun validateParameters(parameters: Parameters, setOfKeys: Set<String>) {
    val keys = parameters.toMap().keys
    val missingParameters = setOfKeys.subtract(keys)
    if (missingParameters.isNotEmpty()) throw InvalidParametersException(missingParameters)
}

fun validateParameters(parameters: Map<String,Any>, setOfKeys: Set<String>) {
    val keys = parameters.keys
    val missingParameters = setOfKeys.subtract(keys)
    if (missingParameters.isNotEmpty()) throw InvalidParametersException(missingParameters)
}

class InvalidParametersException(val parameters:Set<String>):Exception()