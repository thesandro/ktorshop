package com.highsteaks.tools

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

open class SimpleJWT(secret: String) {
    private fun expiresAt() = Date(System.currentTimeMillis() + 3_600_000 * 24 * 15) // 24 hours
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).build()
    fun sign(id: Int): String = JWT.create().withClaim("user_id", id).withExpiresAt(expiresAt()).sign(algorithm)
}