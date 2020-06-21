package com.example.routes

import com.example.Posts
import com.example.Users
import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.response.respond
import io.ktor.routing.Routing
import io.ktor.routing.get
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.transaction


fun Routing.posts(){
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

}