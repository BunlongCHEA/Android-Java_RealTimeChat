package com.project.realtimechatui.api;

import com.project.realtimechatui.api.models.BaseDTO;
import com.project.realtimechatui.api.models.ChatMessage;
import com.project.realtimechatui.api.models.ChatRoom;
import com.project.realtimechatui.api.models.LoginRequest;
import com.project.realtimechatui.api.models.LoginResponse;
import com.project.realtimechatui.api.models.Participant;
import com.project.realtimechatui.api.models.RegisterRequest;
import com.project.realtimechatui.api.models.RegisterResponse;
import com.project.realtimechatui.api.models.TokenRefreshResponse;
import com.project.realtimechatui.api.models.User;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.DELETE;
import retrofit2.http.GET;
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


    // Participant endpoints
    @GET("participants/user/{userId}")
    Call<BaseDTO<List<Participant>>> getParticipantsByUserId(@Path("userId") Long userId);

    @GET("participants/user/{userId}/chat-partners")
    Call<BaseDTO<List<Participant>>> getChatPartners(@Path("userId") Long userId);

    @GET("participants/user/{userId}/personal-chat-partners")
    Call<BaseDTO<List<Participant>>> getPersonalChatPartners(@Path("userId") Long userId);

    @PUT("participants/user/{userId}/room/{chatRoomId}/read")
    Call<BaseDTO<Participant>> updateLastReadMessageId(
            @Path("userId") Long userId,
            @Path("chatRoomId") Long chatRoomId,
            @Query("messageId") Long messageId
    );

    @PUT("participants/user/{userId}/online")
    Call<BaseDTO<Participant>> updateOnlineStatus(
            @Path("userId") Long userId,
            @Query("online") boolean online
    );

    @PUT("participants/user/{userId}/lastseen")
    Call<BaseDTO<Participant>> updateLastSeen(
            @Path("userId") Long userId,
            @Query("lastSeen") String lastSeen
    );


    // Chat room endpoints
    @GET("rooms")
    Call<BaseDTO<List<ChatRoom>>> getAllChatRooms();

    @GET("rooms/{id}")
    Call<BaseDTO<ChatRoom>> getChatRoomById(@Path("id") Long id);

    @POST("rooms")
    Call<BaseDTO<ChatRoom>> createChatRoom(@Body ChatRoom chatRoomDTO, @Query("currentUserId") Long currentUserId);

    @PUT("rooms/{id}")
    Call<BaseDTO<ChatRoom>> updateChatRoom(@Path("id") Long id, @Body ChatRoom chatRoomDTO, @Query("currentUserId") Long currentUserId);

    @DELETE("rooms/{id}")
    Call<BaseDTO<Void>> deleteChatRoom(@Path("id") Long id, @Query("userId") Long userId, @Query("deleteForAll") Boolean deleteForAll);

    // Chat message endpoints
    @GET("messages/chat-room/{chatRoomId}")
    Call<BaseDTO<List<ChatMessage>>> getMessagesByChatRoom(@Path("chatRoomId") Long chatRoomId, @Query("page") int page, @Query("size") int size);

}
