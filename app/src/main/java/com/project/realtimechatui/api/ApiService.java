package com.project.realtimechatui.api;

import com.project.realtimechatui.MainActivity;
import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.LoginRequest;
import com.project.realtimechatui.api.models.LoginResponse;
import com.project.realtimechatui.api.models.RegisterRequest;
import com.project.realtimechatui.api.models.RegisterResponse;
import com.project.realtimechatui.api.models.TokenRefreshResponse;
import com.project.realtimechatui.api.models.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.PUT;
import retrofit2.http.Path;
import retrofit2.http.Query;

public interface ApiService {
    // Authentication Endpoints
    @POST("auth/login")
    Call<BaseDTO<LoginResponse>> login(@Body LoginRequest loginRequest);

    @POST("auth/register")
    Call<BaseDTO<RegisterResponse>> register(@Body RegisterRequest registerRequest);

    @POST("auth/refresh-token")
    Call<BaseDTO<TokenRefreshResponse>> refreshToken(@Body RegisterRequest request);


    // Chat Room Endpoints - matching your backend /api/rooms
    @GET("rooms")
    Call<BaseDTO<List<ChatRoom>>> getChatRooms();

    @GET("rooms/{id}")
    Call<BaseDTO<ChatRoom>> getChatRoom(@Path("id") Long id);

    @POST("rooms")
    Call<BaseDTO<ChatRoom>> createChatRoom(@Body ChatRoom chatRoomDTO, @Query("currentUserId") Long currentUserId);

    @PUT("rooms/{id}")
    Call<BaseDTO<ChatRoom>> updateChatRoom(@Path("id") Long id, @Body ChatRoom chatRoomDTO, @Query("currentUserId") Long currentUserId);

    @DELETE("rooms/{id}")
    Call<BaseDTO<Void>> deleteChatRoom(@Path("id") Long id, @Query("userId") Long userId, @Query("deleteForAll") Boolean deleteForAll);

    // Message Endpoints - matching your backend /api/messages
    @GET("messages/{id}")
    Call<BaseDTO<ChatMessage>> getChatMessageById(@Path("id") Long id);

    @GET("messages/room/{chatRoomId}")
    Call<BaseDTO<List<ChatMessage>>> getChatMessagesByChatRoomId(@Path("chatRoomId") Long chatRoomId);

    @GET("messages/unread/{chatRoomId}")
    Call<BaseDTO<Object>> getUnreadCount(@Path("chatRoomId") Long chatRoomId, @Header("Authorization") String token);
}
