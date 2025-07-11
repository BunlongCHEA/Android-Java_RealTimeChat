package com.project.realtimechatui.api;

import com.project.realtimechatui.utils.Constants;
import com.project.realtimechatui.utils.SharedPrefManager;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class ApiClient {
    private static Retrofit retrofit;
    private static ApiService apiService;

    public static ApiService getApiService() {
        if (apiService == null) {
            apiService = getRetrofitInstance().create(ApiService.class);
        }
        return apiService;
    }

    private static Retrofit getRetrofitInstance() {
        if (retrofit == null) {
            // Create logging interceptor
            HttpLoggingInterceptor loggingInterceptor = new HttpLoggingInterceptor();
            loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

            // Create auth interceptor
            Interceptor authInterceptor = new Interceptor() {
                @Override
                public Response intercept(Chain chain) throws IOException {
                    Request originalRequest = chain.request();

                    // Skip adding token for login/register endpoints
                    String url = originalRequest.url().toString();
                    if (url.contains("/auth/login") || url.contains("/auth/register")) {
                        return chain.proceed(originalRequest);
                    }

                    // Add authorization header for other endpoints
                    String token = SharedPrefManager.getInstance().getAccessToken();
                    if (token != null && !token.isEmpty()) {
                        Request.Builder builder = originalRequest.newBuilder()
                                .addHeader("Authorization", "Bearer " + token)
                                .addHeader("Content-Type", "application/json");
                        return chain.proceed(builder.build());
                    }

                    return chain.proceed(originalRequest);
                }
            };

            // Create OkHttp client
            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(loggingInterceptor)
                    .addInterceptor(authInterceptor)
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(30, TimeUnit.SECONDS)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(Constants.BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }
        return retrofit;
    }
}
