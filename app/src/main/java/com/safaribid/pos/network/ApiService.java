package com.safaribid.pos.network;

import com.safaribid.pos.models.OrdersResponse;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.Query;

public interface ApiService {

    @GET("orders")
    Call<OrdersResponse> getOrders(
            @Header("Authorization") String bearerToken,
            @Query("uid") String userId
    );
}