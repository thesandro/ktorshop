package com.example.routes

import com.example.Users
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun Routing.login(){
    post("/login") {
        val parameters = call.receiveParameters()
        var userID = 0
        transaction {
            SchemaUtils.create(Users)
            val email = parameters["email"] ?: throw InvalidCredentialsException("email missing")
            val password = parameters["password"] ?: throw InvalidCredentialsException("password missing")
            val user = Users.select { (Users.email eq email) and (Users.password eq password) }
                    .singleOrNull() ?: throw InvalidCredentialsException("Invalid credentials.")
            userID = user[Users.id]
        }
        //call.respond(HttpStatusCode.OK,mapOf("token" to simpleJwt.sign(userID)))
        call.respond(HttpStatusCode.OK, mapOf("token" to userID))
    }
}