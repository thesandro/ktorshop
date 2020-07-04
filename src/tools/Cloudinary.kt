package com.example.tools

import com.example.retrofit.ApiClient
import com.google.gson.JsonParser
import io.ktor.http.content.PartData
import io.ktor.http.content.streamProvider
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream

suspend fun uploadToCloudinary(part:PartData.FileItem):String{
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
    val jsonObject = JsonParser.parseString(string).asJsonObject
    val url = jsonObject.get("url")
    return url.asString
}