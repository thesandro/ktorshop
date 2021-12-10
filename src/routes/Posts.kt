package com.highsteaks.routes

import com.highsteaks.database.PostUrls
import com.highsteaks.database.Posts
import com.highsteaks.database.Users
import com.highsteaks.tools.uploadToCloudinary
import com.highsteaks.tools.validateParameters
import com.google.gson.Gson
import com.highsteaks.models.ImageUrl
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.logging.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.serialization.protobuf.ProtoBuf
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.posts(){
    get("/posts") {
        val responseMap = mutableMapOf<String,Any>()
        val resultList = mutableListOf<Map<String, Any>>()
        val noPage = call.request.queryParameters["page"].isNullOrEmpty()
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(Posts)

            val postPerPage = 5
            val page = call.request.queryParameters["page"]?.toIntOrNull() ?: 0
            val postQuery = if(noPage){
                Posts.selectAll()
            }
            else{
                Posts.selectAll().orderBy(Posts.id,SortOrder.DESC).limit(postPerPage,postPerPage* page)
            }
            for (item in postQuery) {
                val urlArray = arrayListOf<Map<String,Any>>()
                for (postUrl in PostUrls.select(PostUrls.owner eq item[Posts.id])) {
                    urlArray.add(mapOf<String,Any>(
                        PostUrls.url.name to postUrl[PostUrls.url],
                        PostUrls.imageHeight.name to postUrl[PostUrls.imageHeight],
                        PostUrls.imageWidth.name to postUrl[PostUrls.imageWidth],
                        PostUrls.format.name to postUrl[PostUrls.format]
                    ))
                }
                val postOwnerInfoQuery = Users.select(Users.id eq item[Posts.owner]).singleOrNull()

                val postMap = mapOf(
                    Posts.id.name to item[Posts.id],
                    Posts.owner.name to item[Posts.owner],
                    Posts.title.name to item[Posts.title],
                    Posts.description.name to item[Posts.description],
                    Posts.categoryID.name to item[Posts.categoryID],
                    "urls" to urlArray,
                    Posts.tags.name to item[Posts.tags],
                    Posts.price.name to item[Posts.price],
                    Posts.priceType.name to item[Posts.priceType],
                    Users.fullName.name to (postOwnerInfoQuery?.get(Users.fullName) ?: "")
                )
                resultList.add(postMap)
            }
            responseMap["result"]  = resultList
            responseMap["nextPage"] = page+1
        }
        if(noPage)
            call.respond(HttpStatusCode.OK,resultList)
        else
            call.respond(HttpStatusCode.OK, responseMap)
    }
}


fun Route.deletePost(){
    delete("/delete-post") {
        val parameters = call.receiveParameters()
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(Posts)
            validateParameters(
                parameters,
                setOf("user_id","post_id")
            )
            val userId = parameters["user_id"]!!
            if (call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
            val postId = parameters["post_id"]!!
            if (call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
            Users.select { (Users.id eq userId.toInt()) }.singleOrNull()
                ?: throw InvalidCredentialsException("user_id doesn't exist.")
            Posts.select { (Posts.id eq postId.toInt()) }.singleOrNull()
                ?: throw InvalidCredentialsException("post doesn't exist.")
            Posts.deleteWhere { (Posts.owner eq userId.toInt()) and (Posts.id eq postId.toInt()) }
        }
        call.respond(HttpStatusCode.OK, mapOf("OK" to true, "post deleted" to (true)))
    }
}

fun Route.createPost(){
    post("/create-post") {
        val multipart = call.receiveMultipart()
        val formPart = mutableMapOf<String, String>()
        val arrayList = arrayListOf<ImageUrl>()
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
        validateParameters(
            formPart,
            setOf(
                "user_id",
                "title",
                "description",
                "category_id",
                "tags",
                "price",
                "price_type"
            )
        )
        val userId = formPart["user_id"]!!
        val title = formPart["title"]!!
        val description = formPart["description"]!!
        val categoryID = formPart["category_id"]!!
        val tags = formPart["tags"]!!
        val price = formPart["price"]!!
        val priceType = formPart["price_type"]!!
        var postId = -1
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(Posts)
            SchemaUtils.create(PostUrls)
            if (arrayList.size == 0) throw InvalidCredentialsException("file missing")
            if (call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
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

            for (url in arrayList) {
                PostUrls.insert {
                    it[PostUrls.owner] = postId
                    it[PostUrls.url] = url.url
                    it[PostUrls.imageHeight] = url.height
                    it[PostUrls.imageWidth] = url.width
                    it[PostUrls.format] = url.format
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
                header("Authorization", "Basic MzgwOTFhZWQtNjY2Ni00ZTYwLWEzYTMtNmYxZjA3YjRkYjk0")
                header("Content-Type", "application/json")
            }
            val gson = Gson()
            body = gson.toJson(map).toString()
        }
        call.respond(HttpStatusCode.OK, mapOf("OK" to true, "posted" to (true)))
    }
}

fun Route.postDetail(){
    post("/get-post-detail") {
        val parameters = call.receiveParameters()
        val postMap = mutableMapOf<String, Any>()
        transaction {
            SchemaUtils.create(Users)
            SchemaUtils.create(Posts)
            val postId = parameters["post_id"] ?: throw InvalidCredentialsException("post_id missing")
            val post = Posts.select { (Posts.id eq postId.toInt()) }.singleOrNull()
                ?: throw InvalidCredentialsException("post doesn't exist.")
            val urlArray = arrayListOf<String>()
            for (postUrl in PostUrls.select(PostUrls.owner eq post[Posts.id])) {
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