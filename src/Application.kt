package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.example.Posts.references
import com.example.UserProfile.autoIncrement
import com.example.UserProfile.primaryKey
import com.example.UserProfile.references
import com.example.UserProfile.uniqueIndex
import com.example.tools.uploadToCloudinary
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.authenticate
import io.ktor.auth.jwt.jwt
import io.ktor.auth.principal
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.client.features.logging.DEFAULT
import io.ktor.client.features.logging.LogLevel
import io.ktor.client.features.logging.Logger
import io.ktor.client.features.logging.Logging
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
import io.ktor.request.receive
import io.ktor.request.receiveChannel
import io.ktor.request.receiveMultipart
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.delete
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.util.KtorExperimentalAPI
import io.ktor.util.hex
import io.ktor.utils.io.core.readBytes
import kotlinx.css.header
import org.apache.http.Header
import org.apache.http.auth.InvalidCredentialsException
import org.h2.util.json.JSONObject
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import java.sql.Connection
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
                            val url = uploadToCloudinary(part)
                            formPart["profile_url"] = url
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
                    val cardHolderName = formPart["card_holder_name"] ?: throw InvalidCredentialsException("card_holder_name missing")
                    val expiryData = formPart["expiry_date"] ?: throw InvalidCredentialsException("expiry_date missing")
                    val securityCode = formPart["security_code"] ?: throw InvalidCredentialsException("security_code missing")
                    val floorApartment = formPart["floor_apartment"] ?: throw InvalidCredentialsException("floor_apartment missing")
                    val profileUrl = formPart["profile_url"] ?: throw InvalidCredentialsException("file missing")
                    if(call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
                    Users.select { (Users.id eq userId.toInt()) }.singleOrNull() ?: throw InvalidCredentialsException("user_id doesn't exist.")
                    val completeProfile = UserProfile.select { (UserProfile.id eq userId.toInt()) }.singleOrNull()
                    if(completeProfile != null) throw InvalidCredentialsException("Profile is completed.")
                    UserProfile.insert {
                        it[UserProfile.owner] = userId.toInt()
                        it[UserProfile.locationAddress] = locationAddress
                        it[UserProfile.profileUrl] = profileUrl
                        it[UserProfile.cardNumber] = cardNumber
                        it[UserProfile.cardHolderName] = cardHolderName
                        it[UserProfile.expiryData] = expiryData
                        it[UserProfile.securityCode] = securityCode
                        it[UserProfile.floorApartment] = floorApartment
                        }
                }
                call.respond(HttpStatusCode.OK, mapOf("OK" to true, "profile completed" to (true)))
            }

            post("profile"){
                val parameters = call.receiveParameters()
                val userId = parameters["user_id"] ?: throw InvalidCredentialsException("user_id missing")
                if(call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
                var userProfile = mapOf<String,Any>()
                transaction {
                    SchemaUtils.create(Users)
                    SchemaUtils.create(UserProfile)


                    val completeProfile = UserProfile.select { (UserProfile.owner eq userId.toInt()) }.singleOrNull()
                    if (completeProfile != null) {
                        val fullProfile = (Users innerJoin UserProfile).slice(
                                Users.email,
                                Users.fullName,
                                UserProfile.profileUrl,
                                UserProfile.locationAddress,
                                UserProfile.cardNumber,
                                UserProfile.cardHolderName,
                                UserProfile.expiryData,
                                UserProfile.securityCode,
                                UserProfile.floorApartment
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
                                UserProfile.securityCode.name to fullProfile[UserProfile.securityCode],
                                UserProfile.floorApartment.name to fullProfile[UserProfile.floorApartment]
                        )
                    }
                    else{
                        val fullProfile = Users.select(Users.id eq userId.toInt()).singleOrNull() ?: throw InvalidCredentialsException("user_id doesn't exist.")
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
        post("/GetByBarcode") {
            val posts = mutableListOf<Map<String, Any>>()
            val map = arrayOf(mapOf(
                            "barcode" to "1111111112",
                            "name" to "შაურმა",
                            "price" to "200000",
                            "measurement" to "ცალი"
                    ),
                    mapOf(
                            "barcode" to "42242424",
                            "name" to "მწვადი",
                            "price" to "50",
                            "measurement" to "ცალი"
                    )
                    ,
                    mapOf(
                            "barcode" to "75765868",
                            "name" to "მწვადი",
                            "price" to "4242",
                            "measurement" to "ცალი"
                    )
            ).random()
            call.respond(HttpStatusCode.OK, map)
        }

        post("/GetByBarcode") {
            val posts = mutableListOf<Map<String, Any>>()
            val map = arrayOf(mapOf(
                    "barcode" to "1111111112",
                    "name" to "შაურმა",
                    "price" to "200000",
                    "measurement" to "ცალი"
            ),
                    mapOf(
                            "barcode" to "42242424",
                            "name" to "მწვადი",
                            "price" to "50",
                            "measurement" to "ცალი"
                    )
                    ,
                    mapOf(
                            "barcode" to "75765868",
                            "name" to "მწვადი",
                            "price" to "4242",
                            "measurement" to "ცალი"
                    )
            ).random()
            call.respond(HttpStatusCode.OK, map)
        }


        get("/animalfacts"){
            val posts = mutableListOf<Map<String, Any>>()
            val map = arrayOf(mapOf(
                    "animal" to "cat",
                    "photo" to "https://ichef.bbci.co.uk/news/1024/cpsprodpb/83D7/production/_111515733_gettyimages-1208779325.jpg"
            ),
                    mapOf(
                            "animal" to "dog",
                            "photo" to "https://i.insider.com/5484d9d1eab8ea3017b17e29?width=600&format=jpeg&auto=webp"
                    )
            ).random()
            call.respond(HttpStatusCode.OK, map)
        }


        post("/sell") {
            val paramters = call.receive<String>()
            print(paramters)
            call.respond(HttpStatusCode.OK)
        }


        get("/posts") {
            val posts = mutableListOf<Map<String, Any>>()
            transaction {
                SchemaUtils.create(Users)
                SchemaUtils.create(Posts)
                for (item in Posts.selectAll()) {
                    val urlArray = arrayListOf<String>()
                    for(postUrl in PostUrls.select(PostUrls.owner eq item[Posts.id])){
                        urlArray.add(postUrl[PostUrls.url])
                    }
                    val postMap = mapOf(
                            Posts.id.name to item[Posts.id],
                            Posts.owner.name to item[Posts.owner],
                            Posts.title.name to item[Posts.title],
                            Posts.description.name to item[Posts.description],
                            Posts.categoryID.name to item[Posts.categoryID],
                            "urls" to urlArray,
                            Posts.tags.name to item[Posts.tags],
                            Posts.price.name to item[Posts.price],
                            Posts.priceType.name to item[Posts.priceType]
                    )
                    posts.add(postMap)
                }
            }
            call.respond(HttpStatusCode.OK, posts)
        }
        authenticate {
            delete("/delete-post") {
                val parameters = call.receiveParameters()
                transaction {
                    SchemaUtils.create(Users)
                    SchemaUtils.create(Posts)
                    val userId = parameters["user_id"] ?: throw InvalidCredentialsException("user_id missing")
                    if(call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
                    val postId = parameters["post_id"] ?: throw InvalidCredentialsException("post_id missing")
                    if(call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
                    Users.select { (Users.id eq userId.toInt()) }.singleOrNull() ?: throw InvalidCredentialsException("user_id doesn't exist.")
                    Posts.select { (Posts.id eq postId.toInt()) }.singleOrNull() ?: throw InvalidCredentialsException("post doesn't exist.")
                    Posts.deleteWhere {(Posts.owner eq userId.toInt()) and (Posts.id eq postId.toInt())}
                }
                call.respond(HttpStatusCode.OK, mapOf("OK" to true, "post deleted" to (true)))
            }
        }
        authenticate {
            post("/create-post") {
                val multipart = call.receiveMultipart()
                val formPart = mutableMapOf<String, String>()
                val arrayList = arrayListOf<String>()
                multipart.forEachPart { part ->
                    when (part) {
                        is PartData.FormItem -> {
                            formPart[part.name!!] = part.value
                        }
                        is PartData.FileItem -> {
                            val url = uploadToCloudinary(part)
                            arrayList.add(url)
                        }
                        is PartData.BinaryItem -> {
                            "BinaryItem(${part.name},${hex(part.provider().readBytes())})"
                        }
                    }
                    part.dispose()
                }
                val userId = formPart["user_id"] ?: throw InvalidCredentialsException("user_id missing")
                val title = formPart["title"] ?: throw InvalidCredentialsException("title missing")
                val description = formPart["description"] ?: throw InvalidCredentialsException("description missing")
                val categoryID = formPart["category_id"] ?: throw InvalidCredentialsException("category missing")
                val tags = formPart["tags"] ?: throw InvalidCredentialsException("tags missing")
                val price = formPart["price"] ?: throw InvalidCredentialsException("price missing")
                val priceType = formPart["price_type"] ?: throw InvalidCredentialsException("price type missing")
                var postId = -1
                transaction {
                    SchemaUtils.create(Users)
                    SchemaUtils.create(Posts)
                    SchemaUtils.create(PostUrls)
                    if(arrayList.size == 0) throw InvalidCredentialsException("file missing")
                    if(call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
                    Users.select { (Users.id eq userId.toInt()) }.singleOrNull()
                            ?: throw InvalidCredentialsException("user_id doesn't exist.")
                    postId = Posts.insert {
                        it[owner] = userId.toInt()
                        it[Posts.title] = title
                        it[Posts.description] = description
                        it[Posts.categoryID] = categoryID.toInt()
                        it[Posts.tags] = tags
                        it[Posts.price] = price.toFloat()
                        it[Posts.priceType] = priceType
                    } get Posts.id

                    for(url in arrayList) {
                        PostUrls.insert {
                            it[PostUrls.owner] = postId
                            it[PostUrls.url] = url
                        }
                    }
                }
                val client = HttpClient(Apache) {
                    install(Logging) {
                        logger = Logger.DEFAULT
                        level = LogLevel.BODY
                    }
                }
                val map = mapOf(
                        "app_id" to "41bcc35a-a29c-4ada-8a2b-4e9da2470dbd",
                      //  "contents" to mapOf("en" to title),
                      //  "headings" to mapOf("en" to "New Post!"),
                      //  "url" to "https://onesignal.com",
                        "content_available" to true, // for silent
                        "included_segments" to arrayOf("All"),
                        "data" to mapOf(
                                "action_id" to "1",
                                "title" to "New post!",
                                "description" to title,
                                "post_id" to postId
                        )
                )
                client.post<String>("https://onesignal.com/api/v1/notifications") {
                    headers {
                        header("Authorization","Basic MzgwOTFhZWQtNjY2Ni00ZTYwLWEzYTMtNmYxZjA3YjRkYjk0")
                        header("Content-Type","application/json")
                    }
                    val gson = Gson()
                    body = gson.toJson(map).toString()
                }
                call.respond(HttpStatusCode.OK, mapOf("OK" to true, "posted" to (true)))
            }
        }

        post("/get-post-detail") {
            val parameters = call.receiveParameters()
            val postMap = mutableMapOf<String,Any>()
            transaction {
                SchemaUtils.create(Users)
                SchemaUtils.create(Posts)
                val postId = parameters["post_id"] ?: throw InvalidCredentialsException("post_id missing")
                val post = Posts.select { (Posts.id eq postId.toInt()) }.singleOrNull() ?: throw InvalidCredentialsException("post doesn't exist.")
                val urlArray = arrayListOf<String>()
                for(postUrl in PostUrls.select(PostUrls.owner eq post[Posts.id])){
                    urlArray.add(postUrl[PostUrls.url])
                }
                postMap[Posts.id.name] = post[Posts.id]
                postMap[Posts.owner.name] = post[Posts.owner]
                postMap[Posts.title.name] = post[Posts.title]
                postMap[Posts.description.name] = post[Posts.description]
                postMap[Posts.categoryID.name] = post[Posts.categoryID]
                postMap["urls"] = urlArray
                postMap[Posts.tags.name] = post[Posts.tags]
                postMap[Posts.price.name] = post[Posts.price]
                postMap[Posts.priceType.name] = post[Posts.priceType]
            }

            call.respond(HttpStatusCode.OK, postMap)
        }
    }

}

open class SimpleJWT(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).build()
    fun sign(id: Int): String = JWT.create().withClaim("user_id", id).withExpiresAt(expiresAt()).sign(algorithm)
}
private fun expiresAt() = Date(System.currentTimeMillis() + 3_600_000 * 24*15) // 24 hours

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
    val floorApartment = varchar("floor_apartment",length = 80)
}


object  PostUrls: Table(){
    val id = integer("id").autoIncrement().primaryKey()
    val owner = integer("owner").references(Posts.id, ReferenceOption.CASCADE)
    val url = varchar("url", length = 80)
}
