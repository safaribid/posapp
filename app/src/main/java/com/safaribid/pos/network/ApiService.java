package com.safaribid.pos.network;

import com.safaribid.pos.models.DeliveryListResponse;
import com.safaribid.pos.models.OrderUpdateResponse;
import com.safaribid.pos.models.OrdersResponse;
import com.safaribid.pos.models.TrackDeliveryResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;
import retrofit2.http.Query;
import retrofit2.http.Url;

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

    /**
     * Full URL, e.g.
     * https://api.safaribid.com/api/account/customer/deliveries/track?code=SB...
     */
    @GET
    Call<TrackDeliveryResponse> trackDelivery(
            @Header("Authorization") String bearerToken,
            @Url String url
    );

    /**
     * Full URL, e.g.
     * https://api.safaribid.com/api/business/deliveries?uid=...
     */
    @GET
    Call<DeliveryListResponse> getBusinessDeliveries(
            @Header("Authorization") String bearerToken,
            @Url String url
    );

    /**
     * Full URL, e.g.
     * https://api.safaribid.com/api/business/deliveries/view?uid=...&id=...
     */
    @GET
    Call<TrackDeliveryResponse> getBusinessDelivery(
            @Header("Authorization") String bearerToken,
            @Url String url
    );
}
