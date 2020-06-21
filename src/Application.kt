package com.example

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.fasterxml.jackson.databind.SerializationFeature
import com.google.gson.JsonParser
import io.ktor.application.Application
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.auth.Authentication
import io.ktor.auth.UserIdPrincipal
import io.ktor.auth.jwt.jwt
import io.ktor.client.HttpClient
import io.ktor.client.engine.apache.Apache
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.jackson.JacksonConverter
import io.ktor.jackson.jackson
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
import org.jetbrains.exposed.sql.transactions.TransactionManager
import org.jetbrains.exposed.sql.transactions.transaction
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap
import java.io.ByteArrayOutputStream
import java.sql.Connection

fun main(args: Array<String>) {

    io.ktor.server.netty.EngineMain.main(args)
    val port = System.getenv("PORT")?.toInt() ?: 23567
    embeddedServer(Netty, port) {

    }.start(wait = true)
}

interface ApiInterface {
    @Multipart
    @POST("upload")
    suspend fun uploadData(
            @Part foto: MultipartBody.Part,
            @PartMap parameters: Map<String, String>
    ): String
}
object ApiClient {

    private val BASE_URL = "https://api.cloudinary.com/v1_1/dcu6ulr6e/image/"

    val getApiClient: ApiInterface by lazy {
        val retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(ScalarsConverterFactory.create())
                .build()

        retrofit.create(ApiInterface::class.java)

    }

}

@KtorExperimentalAPI
@kotlin.jvm.JvmOverloads
fun Application.module(testing: Boolean = false) {

    install(StatusPages) {
        exception<InvalidCredentialsException> { exception ->
            call.respond(HttpStatusCode.Unauthorized, mapOf("OK" to false, "error" to (exception.message ?: "")))
        }
    }
    val simpleJwt = SimpleJWT("my-super-secret-for-jwt")

    install(Authentication) {
        jwt {
            verifier(simpleJwt.verifier)
            validate {
                UserIdPrincipal(it.payload.getClaim("name").asString())
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
                            Posts.url.name to item[Posts.url]
                    ))

                }
            }
            call.respond(HttpStatusCode.OK, posts)
        }

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
                        val string = ApiClient.getApiClient.uploadData(photo,map)
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
                val description = formPart["description"] ?: throw InvalidCredentialsException("description missing")
                val categoryID = formPart["category_id"]  ?: throw InvalidCredentialsException("category missing")
                val filePath = formPart["url"] ?: throw InvalidCredentialsException("file missing")
                Users.select { (Users.id eq userId.toInt()) }
                        .singleOrNull() ?: throw InvalidCredentialsException("user_id doesn't exist.")
                Posts.insert {
                    it[Posts.owner] = userId.toInt()
                    it[Posts.title] = title
                    it[Posts.description] = description
                    it[Posts.categoryID] = categoryID.toInt()
                    it[Posts.url] = filePath
                    //it[Posts.file] = file
                }
            }
            call.respond(HttpStatusCode.OK, mapOf("OK" to true, "posted" to (true)))
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

}


open class SimpleJWT(secret: String) {
    private val algorithm = Algorithm.HMAC256(secret)
    val verifier: JWTVerifier = JWT.require(algorithm).build()
    fun sign(id: Int): String = JWT.create().withClaim("id", id).sign(algorithm)
}

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
}
