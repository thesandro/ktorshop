package com.example.retrofit

import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.PartMap

interface ApiInterface {
    @Multipart
    @POST("upload")
    suspend fun uploadData(
            @Part photo: MultipartBody.Part,
            @PartMap parameters: Map<String, String>
    ): String
}