package com.example.retrofit

import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory

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