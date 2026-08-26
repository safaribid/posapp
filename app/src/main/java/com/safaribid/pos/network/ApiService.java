package com.safaribid.pos.network;

import com.safaribid.pos.models.OrderUpdateResponse;
import com.safaribid.pos.models.OrdersResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;

public interface ApiService {

    @GET("orders")
    Call<OrdersResponse> getOrders(
            @Header("Authorization") String bearerToken,
            @Query("uid") String userId
    );

    @POST("orders/update-status")
    Call<OrderUpdateResponse> updateOrderStatus(
            @Header("Authorization") String bearerToken,
            @Body Map<String, Object> body
    );

    @GET("orders/detail")
    Call<com.safaribid.pos.models.Order> getOrderById(
            @Header("Authorization") String bearerToken,
            @Query("id") String orderId
    );
}
