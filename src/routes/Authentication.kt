package com.highsteaks.routes

import com.highsteaks.database.Users
import com.highsteaks.tools.SimpleJWT
import com.highsteaks.tools.validateParameters
import io.ktor.application.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.login(simpleJwt: SimpleJWT){
    post("/login") {
        val parameters = call.receiveParameters()
        var userID = 0
        transaction {
            SchemaUtils.create(Users)
            validateParameters(parameters, setOf("email", "password"))
            val email = parameters["email"]!!
            val password = parameters["password"]!!
            val user = Users.select { (Users.email eq email) and (Users.password eq password) }
                .singleOrNull() ?: throw InvalidCredentialsException("Invalid credentials.")
            userID = user[Users.id]
        }
        call.respond(HttpStatusCode.OK, mapOf("user_id" to userID, "token" to simpleJwt.sign(userID)))
    }
}

fun Route.register(){
    post("/register") {
        val parameters = call.receiveParameters()
        transaction {
            SchemaUtils.create(Users)
            validateParameters(parameters, setOf("email", "password", "full_name"))
            val email = parameters["email"]!!
            val password = parameters["password"]!!
            val fullName = parameters["full_name"]!!
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