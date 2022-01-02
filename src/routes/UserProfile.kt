package com.highsteaks.routes

import com.highsteaks.database.UserProfile
import com.highsteaks.database.Users
import com.highsteaks.models.ImageUrl
import com.highsteaks.tools.uploadToCloudinary
import com.highsteaks.tools.uploadToCloudinaryWithoutDimensions
import com.highsteaks.tools.validateParameters
import io.ktor.application.*
import io.ktor.auth.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import org.apache.http.auth.InvalidCredentialsException
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

fun Route.completeProfile(){
    post("/complete-profile") {
        val multipart = call.receiveMultipart()
        val formPart = mutableMapOf<String, Any>()
        multipart.forEachPart { part ->
            when (part) {
                is PartData.FormItem -> {
                    formPart[part.name!!] = part.value
                }
                is PartData.FileItem -> {
                    val url = uploadToCloudinaryWithoutDimensions(part)
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
            validateParameters(
                formPart,
                setOf(
                    "user_id",
                    "address",
                    "card_number",
                    "card_holder_name",
                    "expiry_date",
                    "security_code",
                    "floor_apartment",
                    "profile_url"
                )
            )
            val userId = formPart["user_id"]!!
            val locationAddress = formPart["address"]!!
            val cardNumber = formPart["card_number"]!!
            val cardHolderName = formPart["card_holder_name"]!!
            val expiryData = formPart["expiry_date"]!!
            val securityCode = formPart["security_code"]!!
            val floorApartment = formPart["floor_apartment"]!!
            val profileUrl = formPart["profile_url"]!!
            if (call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
            Users.select { (Users.id eq userId.toInt()) }.singleOrNull()
                ?: throw InvalidCredentialsException("user_id doesn't exist.")
            val completeProfile = UserProfile.select { (UserProfile.id eq userId.toInt()) }.singleOrNull()
            if (completeProfile != null) throw InvalidCredentialsException("Profile is completed.")
            UserProfile.insert {
                it[owner] = userId.toInt()
                it[UserProfile.locationAddress] = locationAddress as String
                it[UserProfile.profileUrl] = profileUrl as String
                it[UserProfile.cardNumber] = cardNumber as String
                it[UserProfile.cardHolderName] = cardHolderName as String
                it[UserProfile.expiryData] = expiryData as String
                it[UserProfile.securityCode] = securityCode as String
                it[UserProfile.floorApartment] = floorApartment as String
            }
        }
        call.respond(HttpStatusCode.OK, mapOf("OK" to true, "profile completed" to (true)))
    }

}

fun Route.profile(){
    post("profile") {
        val parameters = call.receiveParameters()
        validateParameters(
            parameters,
            setOf("user_id")
        )
        val userId = parameters["user_id"]

        if (call.principal<UserIdPrincipal>()!!.name != userId) throw InvalidCredentialsException("no access to this user_id")
        var userProfile = mapOf<String, Any>()
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
            } else {
                val fullProfile = Users.select(Users.id eq userId.toInt()).singleOrNull()
                    ?: throw InvalidCredentialsException("user_id doesn't exist.")
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