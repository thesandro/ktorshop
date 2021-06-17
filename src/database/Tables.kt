package com.highsteaks.database

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

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
    val tags = varchar("tags", length = 120)
    val price = float("price")
    val priceType = varchar("price_type", length = 80)
}

object UserProfile : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val owner = integer("owner").references(Users.id, ReferenceOption.CASCADE).uniqueIndex()
    val profileUrl = varchar("profile_url", length = 120)
    val locationAddress = varchar("address", length = 250)
    val cardNumber = varchar("card_number", length = 80)
    val cardHolderName = varchar("card_holder_name", length = 120)
    val expiryData = varchar("expiry_date", length = 40)
    val securityCode = varchar("security_code", length = 40)
    val floorApartment = varchar("floor_apartment", length = 80)
}

object PostUrls : Table() {
    val id = integer("id").autoIncrement().primaryKey()
    val owner = integer("owner").references(Posts.id, ReferenceOption.CASCADE)
    val url = varchar("url", length = 255)
    val imageHeight = integer("image_height")
    val imageWidth = integer("image_width")
    val format = varchar("format",length = 80)
}