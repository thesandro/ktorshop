package com.highsteaks.tools

import com.highsteaks.retrofit.ApiClient
import com.google.gson.JsonParser
import com.highsteaks.models.ImageUrl
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

suspend fun uploadToCloudinary(part: PartData.FileItem): ImageUrl {
    val name = part.originalFileName!!
    val outputStream = ByteArrayOutputStream()
    part.streamProvider().use { its ->
        outputStream.buffered().use {
            its.copyTo(it)
        }
    }
    val requestFile = outputStream.toByteArray()
        .toRequestBody("multipart/from-data".toMediaTypeOrNull(), 0, outputStream.toByteArray().size)
    val photo = MultipartBody.Part.createFormData("file", name, requestFile)
    val map = mapOf("upload_preset" to "izwuplfk")
    val string = ApiClient.getApiClient.uploadData(photo, map)
    val jsonObject = JsonParser.parseString(string).asJsonObject

    val url = jsonObject.get("url").asString
    val height = jsonObject.get("height").asInt
    val width = jsonObject.get("width").asInt
    val format = jsonObject.get("format").asString
    return ImageUrl(url = url, height = height, width = width, format = format)
}

suspend fun uploadToCloudinaryWithoutDimensions(part: PartData.FileItem): String {
    val name = part.originalFileName!!
    val outputStream = ByteArrayOutputStream()
    part.streamProvider().use { its ->
        outputStream.buffered().use {
            its.copyTo(it)
        }
    }
    val requestFile = outputStream.toByteArray()
        .toRequestBody("multipart/from-data".toMediaTypeOrNull(), 0, outputStream.toByteArray().size)
    val photo = MultipartBody.Part.createFormData("file", name, requestFile)
    val map = mapOf("upload_preset" to "izwuplfk")
    val string = ApiClient.getApiClient.uploadData(photo, map)
    val jsonObject = JsonParser.parseString(string).asJsonObject

    return jsonObject.get("url").asString
}