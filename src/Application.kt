package com.highsteaks

import com.highsteaks.routes.*
import com.highsteaks.tools.InvalidParametersException
import com.highsteaks.tools.SimpleJWT
import com.fasterxml.jackson.databind.SerializationFeature
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.auth.jwt.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.jackson.*
import io.ktor.response.*
import io.ktor.routing.get
import io.ktor.routing.routing
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.Connection

fun main(args: Array<String>) {

    EngineMain.main(args)
    val port = System.getenv("PORT")?.toInt() ?: 23567
    embeddedServer(Netty, port) {
    }.start(wait = true)
}

@KtorExperimentalAPI
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    install(StatusPages) {
        exception<InvalidParametersException> { exception ->
            call.respond(HttpStatusCode.BadRequest, mapOf("OK" to false, "missing_parameters" to exception.parameters))
        }
        exception<InvalidCredentialsException> { exception ->
            call.respond(HttpStatusCode.BadRequest, mapOf("OK" to false, "error" to (exception.message ?: "")))
        }
    }

    val simpleJwt = SimpleJWT("jwtssaidumloebani")

    install(Authentication) {
        jwt {
            verifier(simpleJwt.verifier)
            validate {
                UserIdPrincipal(it.payload.getClaim("user_id").asInt().toString())
            }
        }
    }

    val config =  HikariConfig().apply {
        jdbcUrl = "jdbc:postgresql://ec2-46-137-123-136.eu-west-1.compute.amazonaws.com:5432/d64lpav8ohk462"
        username = "jxhpjhprjnbevz"
        password = "1990965b8171c02ae030159d22c0db07ef1e30c941e29e6f21b347ac520695f9"

    }
    Database.connect(HikariDataSource(config))
   // Database.connect("jdbc:sqlite:db1", "org.sqlite.JDBC")

    TransactionManager.manager.defaultIsolationLevel = Connection.TRANSACTION_SERIALIZABLE
    HttpClient(Apache) {
    }
    install(ContentNegotiation) {
        jackson {
            enable(SerializationFeature.INDENT_OUTPUT)
        }
        register(ContentType.Application.Json, JacksonConverter())
        register(ContentType.MultiPart.Any, JacksonConverter())
    }

    routing {
        get("/") {
            call.respondText("Twas me who was born!", contentType = ContentType.Text.CSS)
        }
        //routes
        register()
        login(simpleJwt)
        posts()
        postDetail()
        authenticate {
            profile()
            completeProfile()
            createPost()
            deletePost()
        }
    }
}