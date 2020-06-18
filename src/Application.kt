package com.example

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.ktor.application.*
import io.ktor.response.*
import io.ktor.request.*
import io.ktor.routing.*
import io.ktor.http.*
import io.ktor.html.*
import kotlinx.html.*
import kotlinx.css.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.features.ContentNegotiation
import io.ktor.html.insert
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.getValue
import org.jetbrains.exposed.dao.EntityID
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.IntIdTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection


fun main(args: Array<String>): Unit {

    io.ktor.server.netty.EngineMain.main(args)
    val port = System.getenv("PORT")?.toInt() ?: 23567
    embeddedServer(Netty, port) {
    }.start(wait = true)
}

@Suppress("unused") // Referenced in application.conf

@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

// In file
    Database.connect("jdbc:sqlite:identifier.sqlite", "org.sqlite.JDBC")
// For both: set SQLite compatible isolation level, see
// https://github.com/JetBrains/Exposed/wiki/FAQ
    TransactionManager.manager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
    val client = HttpClient(Apache) {
    }
    val install = install(ContentNegotiation) {
        jackson {
            // Configure Jackson's ObjectMapper here
            enable(SerializationFeature.INDENT_OUTPUT)
        }
        register(ContentType.Application.Json, JacksonConverter())
        register(ContentType.MultiPart.Any, JacksonConverter())
    }




    routing {
        get("/") {
            call.respondText("Twas me who was born!", contentType = ContentType.Text.CSS)
        }
        get("/jsonresponse") {
            val map = mutableMapOf<String,String>()
            map["name_of_my_love"] = "bibo"

            call.respond(map)
        }
        post("/getThings"){

            // 'select *' SQL: SELECT Cities.id, Cities.name FROM Cities
            val parameters = call.receiveParameters()
            transaction{
                SchemaUtils.create(Users)
                // insert new city. SQL: INSERT INTO Cities (name) VALUES ('St. Petersburg')
                Users.insert {
                    it[name] = parameters["NAME"]!!
                }
                for( item in  Users.selectAll())
                    println("id: ${item[Users.id]} user: ${item[Users.name]}")
            }
            val map = mutableMapOf<String,Map<String,String>>()
            map["Residential"] = mapOf("1" to "Single Family","2" to "Condo","4" to "Townhouse")
            map["Land"] = mapOf("1" to "Residential","15" to "Agricultural","13" to "Industrial","12" to "Commercial")
            call.respond(map)
        }
        get("/html-dsl") {
            call.respondHtml {
                body {
                    h1 { +"HTML" }
                    ul {
                        for (n in 1..10) {
                            li { +"$n" }
                        }
                    }
                }
            }
        }

        get("/styles.css") {
            call.respondCss {
                kotlinx.css.body {
                    backgroundColor = Color.red
                }
                kotlinx.css.p {
                    fontSize = 2.em
                }
                rule("p.myclass") {
                    color = Color.blue
                }
            }
        }
    }

}

suspend inline fun ApplicationCall.respondCss(builder: CSSBuilder.() -> Unit) {
    this.respondText(CSSBuilder().apply(builder).toString(), ContentType.Text.CSS)
}
object Users : Table() {
    val id = integer("ID").autoIncrement().primaryKey()
    val name = varchar("USER", length = 50) // Column<String>
}
