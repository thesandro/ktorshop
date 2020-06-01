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
import io.ktor.http.content.MultiPartData
import io.ktor.http.content.PartData
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

fun main(args: Array<String>): Unit {
    io.ktor.server.netty.EngineMain.main(args)
    val port = System.getenv("PORT")?.toInt() ?: 23567
    embeddedServer(Netty, port) {

    }.start(wait = true)
}

@Suppress("unused") // Referenced in application.conf
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {


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
        get("/bozirati") {
            call.respondText("შეგეცი რატი აღარ ხარ საჭირო!", contentType = ContentType.Text.Plain)
        }

        post("/fuckyou"){

            val multipart = call.receiveMultipart()
            val array = mapOf<String,Map<String,String>>()
//            while (true) {
//                val part = multipart.readPart() ?: break
//
//                when (part) {
//                    is PartData.FormItem ->
//                        map[part.name!!] = part.value
//                    is PartData.FileItem ->
//                        map[part.name!!] = part.originalFileName.toString()
//                }
//
//                part.dispose()
//            }
            val map = mutableMapOf<String,Map<String,String>>()
            map["Residential"] = mapOf("1" to "Single Family","2" to "Condo","4" to "Townhouse")
            map["Land"] = mapOf("1" to "Residential","15" to "Agricultural","13" to "Industrial","12" to "Commercial")
            call.respond(map)
        }
    }
}
