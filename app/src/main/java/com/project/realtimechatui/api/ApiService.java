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

    // User endpoints
    @GET("users")
    Call<BaseDTO<List<User>>> getAllUsers();

    @GET("users/{id}")
    Call<BaseDTO<User>> getUserById(@Path("id") Long id);

    @GET("users/username")
    Call<BaseDTO<User>> getUserByUsername(@Query("username") String username);

    // Chat room endpoints
    @GET("rooms")
    Call<BaseDTO<List<ChatRoom>>> getChatRooms();

    @GET("rooms/{id}")
    Call<BaseDTO<ChatRoom>> getChatRoomById(@Path("id") Long id);

    @POST("rooms")
    Call<BaseDTO<ChatRoom>> createChatRoom(@Body ChatRoom chatRoomDTO, @Query("currentUserId") Long currentUserId);

    @PUT("rooms/{id}")
    Call<BaseDTO<ChatRoom>> updateChatRoom(@Path("id") Long id, @Body ChatRoom chatRoomDTO, @Query("currentUserId") Long currentUserId);

    @DELETE("rooms/{id}")
    Call<BaseDTO<Void>> deleteChatRoom(@Path("id") Long id, @Query("userId") Long userId, @Query("deleteForAll") Boolean deleteForAll);

    // Chat message endpoints
    @GET("messages/{id}")
    Call<BaseDTO<ChatMessage>> getChatMessageById(@Path("id") Long id);

    @GET("messages/room/{chatRoomId}")
    Call<BaseDTO<List<ChatMessage>>> getChatMessages(@Path("chatRoomId") Long chatRoomId);

    @POST("messages/room/{chatRoomId}/text")
    Call<BaseDTO<ChatMessage>> sendMessage(@Path("chatRoomId") Long chatRoomId, @Query("senderId") Long senderId, @Body String content);

    @POST("messages/room/{chatRoomId}/system")
    Call<BaseDTO<ChatMessage>> createSystemMessage(@Path("chatRoomId") Long chatRoomId, @Body String content);

    @DELETE("messages/{messageId}")
    Call<BaseDTO<Void>> deleteMessage(@Path("messageId") Long messageId, @Query("userId") Long userId);

    @PUT("messages/{messageId}")
    Call<BaseDTO<ChatMessage>> editMessage(@Path("messageId") Long messageId, @Query("userId") Long userId, @Query("newContent") String newContent);

    @GET("messages/room/{chatRoomId}/search")
    Call<BaseDTO<List<ChatMessage>>> searchMessages(@Path("chatRoomId") Long chatRoomId, @Query("searchTerm") String searchTerm);

    @GET("messages/unread/{chatRoomId}")
    Call<BaseDTO<Integer>> getUnreadCount(@Path("chatRoomId") Long chatRoomId);

    // Participant endpoints
    @GET("participants/room/{chatRoomId}")
    Call<BaseDTO<List<User>>> getParticipants(@Path("chatRoomId") Long chatRoomId);

    @POST("participants/room/{chatRoomId}/add")
    Call<BaseDTO<Void>> addParticipant(@Path("chatRoomId") Long chatRoomId, @Query("userId") Long userId);

    @DELETE("participants/room/{chatRoomId}/remove")
    Call<BaseDTO<Void>> removeParticipant(@Path("chatRoomId") Long chatRoomId, @Query("userId") Long userId);
}
