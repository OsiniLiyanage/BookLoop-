package lk.jiat.bookloop.service;

import com.google.gson.JsonObject;

import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Query;

public interface DirectionApi {
    @GET("json")
    Call<JsonObject> getJson(@Query("origin") String origin,
                             @Query("destination") String destination,
                             @Query("key") String key);
}
