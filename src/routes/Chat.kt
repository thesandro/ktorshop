package com.highsteaks.routes

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.highsteaks.database.UserProfile
import com.highsteaks.database.Users
import com.highsteaks.tools.SimpleJWT
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.text.html.HTML.Tag.U

class Connection(val session: DefaultWebSocketSession) {
    companion object {
        var lastId = AtomicInteger(0)
    }
    var userId:String? = null
    var name = "user${lastId.getAndIncrement()}"
    var token:String? = null
}

fun Route.webSocketRoute(simpleJwt: SimpleJWT){
    val connections = Collections.synchronizedSet<Connection?>(LinkedHashSet())
    webSocket("/chat") {
        println("Adding user!")
        val thisConnection = Connection(this)
        connections += thisConnection
        try {
            send("You are connected! There are ${connections.count()} users here.")
            for (frame in incoming) {
                frame as? Frame.Text ?: continue
                val receivedText = frame.readText()
                val receivedJson = Json.decodeFromString<Map<String,String>>(receivedText)
                receivedJson["token"]?.let {
                    thisConnection.token = it
                    val userId = simpleJwt.decodeId(it).asInt()
                    transaction {
                        Users.select { (Users.id eq userId.toInt()) }.singleOrNull()?.let { user ->
                            thisConnection.name = user[Users.fullName]
                        }
                    }
                }
                receivedJson["text"]?.let {
                    text ->
                    val textObject = mapOf(
                        "user" to thisConnection.name,
                        "text" to text
                    )
                    val json = Json.encodeToString(textObject)
                    connections.forEach {
                        it.session.send(json)
                    }
                }
            }
        } catch (e: Exception) {
            println(e.localizedMessage)
        } finally {
            println("Removing $thisConnection!")
            connections -= thisConnection
        }
    }
}