package com.project.realtimechatui.api;

import com.project.realtimechatui.api.models.AuthResponse;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.LoginRequest;
import com.project.realtimechatui.api.models.RegisterRequest;
import com.project.realtimechatui.api.models.User;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {
    // Authentication Endpoints
    @POST("auth/login")
    Call<AuthResponse> login(@Body LoginRequest loginRequest);
    @POST("auth/register")
    Call<AuthResponse> register(@Body RegisterRequest registerRequest);
    @POST("auth/logout")
    Call<Void> logout(@Header("Authorization") String token);
    @GET("auth/me")
    Call<User> getCurrentUser(@Header("Authorization") String token);
    @POST("auth/refresh")
    Call<AuthResponse> refreshToken(@Header("Authorization") String refreshToken);
    @GET("users/{userId}")
    Call<User> getUserById(@Path("userId") Long userId, @Header("Authorization") String token);
    @GET("auth/validate")
    Call<Void> validateToken(@Header("Authorization") String token);
}
