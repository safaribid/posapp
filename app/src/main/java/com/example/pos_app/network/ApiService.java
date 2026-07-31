package com.example.pos_app.network;

import com.example.pos_app.models.Order;

import java.util.List;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Path;

public interface ApiService {

    @GET("orders")
    Call<List<Order>> getOrders(@Header("Authorization") String bearerToken);

    @POST("orders/{id}/accept")
    Call<Order> acceptOrder(
            @Header("Authorization") String bearerToken,
            @Path("id") String orderId
    );

    @POST("orders/{id}/reject")
    Call<Order> rejectOrder(
            @Header("Authorization") String bearerToken,
            @Path("id") String orderId
    );
}