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
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun Routing.register(){
    post("/register") {
        val parameters = call.receiveParameters()
        transaction {
            SchemaUtils.create(Users)
            val email = parameters["email"] ?: throw InvalidCredentialsException("email missing")
            val password = parameters["password"] ?: throw InvalidCredentialsException("password missing")
            val fullName = parameters["full_name"] ?: throw InvalidCredentialsException("full_name missing")
            val user = Users.select { (Users.email eq email) and (Users.password eq password) }
                    .singleOrNull()
            if (user != null) throw InvalidCredentialsException("Email already registered.")
            Users.insert {
                it[Users.email] = email
                it[Users.password] = password
                it[Users.fullName] = fullName
            }
        }
        call.respond(HttpStatusCode.OK, mapOf("OK" to true, "registered" to (true)))
    }
}