package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import com.example.retrofit.ApiClient
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.gson.JsonParser
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.*
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.auth.HttpAuthHeader
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.request.receiveMultipart
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.hex
import io.ktor.utils.io.core.readBytes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream
import java.sql.Connection
import java.text.DateFormat
import java.util.Date

fun main(args: Array<String>) {

    io.ktor.server.netty.EngineMain.main(args)
    val port = System.getenv("PORT")?.toInt() ?: 23567
    embeddedServer(Netty, port) {

    }.start(wait = true)
}


@KtorExperimentalAPI
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    install(StatusPages) {
        exception<InvalidCredentialsException> { exception ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("OK" to false, "error" to (exception.message ?: "")))
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
    Database.connect("jdbc:sqlite:db1", "org.sqlite.JDBC")
    TransactionManager.manager.defaultIsolationLevel =
            Connection.TRANSACTION_SERIALIZABLE
    HttpClient(Apache) {
    }
    install(ContentNegotiation) {
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
            call.respond(HttpStatusCode.OK, mapOf("user_id" to userID,"token" to simpleJwt.sign(userID)))
            //call.respond(HttpStatusCode.OK, mapOf("token" to userID))
        }
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

        authenticate {
            post("/complete-profile") {
                val multipart = call.receiveMultipart()
                val formPart = mutableMapOf<String, String>()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            formPart[part.name!!] = part.value
                        }
                        is PartData.FileItem -> {
                            val name = part.originalFileName!!
                            val outputStream = ByteArrayOutputStream()
                            part.streamProvider().use { its ->
                                outputStream.buffered().use {
                                    its.copyTo(it)
                                }
                            }
                            val requestFile = outputStream.toByteArray().toRequestBody("multipart/from-data".toMediaTypeOrNull(), 0, outputStream.toByteArray().size)
                            val photo = MultipartBody.Part.createFormData("file", name, requestFile)
                            val map = mapOf("upload_preset" to "izwuplfk")
                            val string = ApiClient.getApiClient.uploadData(photo, map)
                            val jsonObject = JsonParser().parse(string).asJsonObject
                            val url = jsonObject.get("url")
                            formPart["profile_url"] = url.asString
                        }
                        is PartData.BinaryItem -> {
                            "BinaryItem(${part.name},${hex(part.provider().readBytes())})"
                        }
                    }
                    part.dispose()
                }
                transaction {
                    SchemaUtils.create(Users)
                    SchemaUtils.create(UserProfile)

                    val userId = formPart["user_id"] ?: throw InvalidCredentialsException("user_id missing")
                    val locationAddress = formPart["address"] ?: throw InvalidCredentialsException("address missing")
                    val cardNumber = formPart["card_number"] ?: throw InvalidCredentialsException("card_number missing")
                    val cardHolderName = formPart["card_holder_name"]
                            ?: throw InvalidCredentialsException("card_holder_name missing")
                    val expiryData = formPart["expiry_date"] ?: throw InvalidCredentialsException("expiry_date missing")
                    val securityCode = formPart["security_code"]
                            ?: throw InvalidCredentialsException("security_code missing")
                    val filePath = formPart["profile_url"] ?: throw InvalidCredentialsException("file missing")
                    Users.select { (Users.id eq userId.toInt()) }.singleOrNull() ?: throw InvalidCredentialsException("user_id doesn't exist.")
                    UserProfile.insert {
                        it[UserProfile.owner] = userId.toInt()
                        it[UserProfile.locationAddress] = locationAddress
                        it[UserProfile.profileUrl] = filePath
                        it[UserProfile.cardNumber] = cardNumber
                        it[UserProfile.cardHolderName] = cardHolderName
                        it[UserProfile.expiryData] = expiryData
                        it[UserProfile.securityCode] = securityCode
                        }
                }
                call.respond(HttpStatusCode.OK, mapOf("OK" to true, "profile completed" to (true)))
            }

            post("profile"){
                val parameters = call.receiveParameters()
                val userId = parameters["user_id"] ?: throw InvalidCredentialsException("user_id missing")
                var userProfile = mapOf<String,Any>()
                transaction {
                    SchemaUtils.create(Users)
                    SchemaUtils.create(Posts)

                    val completeProfile = UserProfile.select { (UserProfile.id eq userId.toInt()) }.singleOrNull()
                    if (completeProfile != null) {
                        val fullProfile = (Users innerJoin UserProfile).slice(
                                Users.email,
                                Users.fullName,
                                UserProfile.profileUrl,
                                UserProfile.locationAddress,
                                UserProfile.cardNumber,
                                UserProfile.cardHolderName,
                                UserProfile.expiryData,
                                UserProfile.securityCode
                        ).select {
                            Users.id eq userId.toInt()
                        }.first()
                        userProfile = mapOf(
                                Users.email.name to fullProfile[Users.email],
                                Users.fullName.name to fullProfile[Users.fullName],
                                UserProfile.profileUrl.name to fullProfile[UserProfile.profileUrl],
                                UserProfile.locationAddress.name to fullProfile[UserProfile.locationAddress],
                                UserProfile.cardNumber.name to fullProfile[UserProfile.cardNumber],
                                UserProfile.cardHolderName.name to fullProfile[UserProfile.cardHolderName],
                                UserProfile.expiryData.name to fullProfile[UserProfile.expiryData],
                                UserProfile.securityCode.name to fullProfile[UserProfile.securityCode]
                        )
                    }
                    else{
                        val fullProfile = Users.selectAll().first()
                        userProfile = mapOf(
                                Users.email.name to fullProfile[Users.email],
                                Users.fullName.name to fullProfile[Users.fullName],
                                "profile-completed" to false
                        )
                    }
                }
                call.respond(HttpStatusCode.OK, userProfile)
            }

        }
        get("/posts") {
            val posts = mutableListOf<Map<String, Any>>()
            transaction {
                SchemaUtils.create(Users)
                SchemaUtils.create(Posts)
                for (item in Posts.selectAll()) {
                    posts.add(mapOf(
                            Posts.owner.name to item[Posts.owner],
                            Posts.title.name to item[Posts.title],
                            Posts.description.name to item[Posts.description],
                            Posts.categoryID.name to item[Posts.categoryID],
                            Posts.url.name to item[Posts.url],
                            Posts.tags.name to item[Posts.tags],
                            Posts.price.name to item[Posts.price],
                            Posts.priceType.name to item[Posts.priceType]
                    ))
                }
            }
            call.respond(HttpStatusCode.OK, posts)
        }
        authenticate {
            post("/create-post") {
                val multipart = call.receiveMultipart()
                val formPart = mutableMapOf<String, String>()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            formPart[part.name!!] = part.value
                        }
                        is PartData.FileItem -> {
                            val name = part.originalFileName!!
                            val outputStream = ByteArrayOutputStream()
                            part.streamProvider().use { its ->
                                outputStream.buffered().use {
                                    its.copyTo(it)
                                }
                            }
                            val requestFile = outputStream.toByteArray().toRequestBody("multipart/from-data".toMediaTypeOrNull(), 0, outputStream.toByteArray().size)
                            val photo = MultipartBody.Part.createFormData("file", name, requestFile)
                            val map = mapOf("upload_preset" to "izwuplfk")
                            val string = ApiClient.getApiClient.uploadData(photo, map)
                            val jsonObject = JsonParser().parse(string).asJsonObject
                            val url = jsonObject.get("url")
                            formPart["url"] = url.asString
                        }
                        is PartData.BinaryItem -> {
                            "BinaryItem(${part.name},${hex(part.provider().readBytes())})"
                        }
                    }
                    part.dispose()
                }

                transaction {
                    SchemaUtils.create(Users)
                    SchemaUtils.create(Posts)
                    val userId = formPart["user_id"] ?: throw InvalidCredentialsException("user_id missing")
                    val title = formPart["title"] ?: throw InvalidCredentialsException("title missing")
                    val description = formPart["description"]
                            ?: throw InvalidCredentialsException("description missing")
                    val categoryID = formPart["category_id"] ?: throw InvalidCredentialsException("category missing")
                    val tags = formPart["tags"] ?: throw InvalidCredentialsException("tags missing")
                    val price = formPart["price"] ?: throw InvalidCredentialsException("price missing")
                    val priceType = formPart["price_type"] ?: throw InvalidCredentialsException("price type missing")
                    val filePath = formPart["url"] ?: throw InvalidCredentialsException("file missing")

                    Users.select { (Users.id eq userId.toInt()) }.singleOrNull()
                            ?: throw InvalidCredentialsException("user_id doesn't exist.")
                    Posts.insert {
                        it[owner] = userId.toInt()
                        it[Posts.title] = title
                        it[Posts.description] = description
                        it[Posts.categoryID] = categoryID.toInt()
                        it[Posts.tags] = tags
                        it[Posts.price] = price.toFloat()
                        it[Posts.priceType] = priceType
                        it[url] = filePath
                    }
                }
                call.respond(HttpStatusCode.OK, mapOf("OK" to true, "posted" to (true)))
            }
        }
    }

}
data class Credentials(val email:String,val password:String)

open class SimpleJWT(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).build()
    fun sign(id: Int): String = JWT.create().withClaim("user_id", id).withExpiresAt(expiresAt()).sign(algorithm)
}
private fun expiresAt() =
        Date(System.currentTimeMillis() + 3_600_000 * 24) // 24 hours

object Users : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val email = varchar("email", length = 50).uniqueIndex() // Column<String>
    val fullName = varchar("full_name", length = 50) // Column<String>
    val password = varchar("password", length = 50) // Column<String>
}

object Posts : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val owner = integer("owner").references(Users.id, ReferenceOption.CASCADE)
    val title = varchar("title", length = 80) // Column<String>
    val description = varchar("description", length = 250) // Column<String>
    val categoryID = integer("category_id")
    val url = varchar("url", length = 80)
    val tags = varchar("tags",length = 120)
    val price = float("price")
    val priceType = varchar("price_type",length = 80)
}


object UserProfile: Table(){
    val id = integer("id").autoIncrement().primaryKey()
    val owner = integer("owner").references(Users.id, ReferenceOption.CASCADE).uniqueIndex()
    val profileUrl = varchar("profile_url",length = 120)
    val locationAddress = varchar("address",length = 250)
    val cardNumber = varchar("card_number", length = 80)
    val cardHolderName = varchar("card_holder_name", length = 120)
    val expiryData = varchar("expiry_date", length = 40)
    val securityCode = varchar("security_code", length = 40)
}
