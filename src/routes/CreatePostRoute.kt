package com.example.routes

import com.example.Posts
import com.example.Users
import com.example.retrofit.ApiClient
import com.google.gson.JsonParser
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.http.content.streamProvider
import io.ktor.request.receiveMultipart
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.post
import io.ktor.util.hex
import io.ktor.utils.io.core.readBytes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.io.ByteArrayOutputStream

fun Routing.createPost(){
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
            Users.select { (Users.id eq userId.toInt()) }.singleOrNull() ?: throw InvalidCredentialsException("user_id doesn't exist.")
            Posts.insert {
                it[owner] = userId.toInt()
                it[Posts.title] = title
                it[Posts.description] = description
                it[Posts.categoryID] = categoryID.toInt()
                it[url] = filePath
            }
        }
        call.respond(HttpStatusCode.OK, mapOf("OK" to true, "posted" to (true)))
    }
}