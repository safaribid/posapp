package com.example.pos_app.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.example.pos_app.utils.AppConfig;

import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class AuthManager {

    private static final String TAG = "AuthManager";
    private static final String PREF_NAME = "pos_auth_prefs";
    private static final String KEY_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";

    private final SharedPreferences prefs;
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;

    public interface AuthCallback {
        void onSuccess(String accessToken);
        void onError(String message);
    }

    public AuthManager(Context context) {
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        client = new OkHttpClient();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public void login(String email, String password, AuthCallback callback) {
        executor.execute(() -> {
            try {
                JSONObject body = new JSONObject();
                body.put("email", email);
                body.put("password", password);

                RequestBody requestBody = RequestBody.create(
                        body.toString(),
                        MediaType.parse("application/json")
                );

                String url = AppConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password";

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("apikey", AppConfig.SUPABASE_PUB_KEY)
                        .addHeader("Content-Type", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    if (response.isSuccessful()) {
                        JSONObject json = new JSONObject(responseBody);
                        String accessToken = json.getString("access_token");
                        String refreshToken = json.optString("refresh_token", "");
                        String userId = "";
                        String userEmail = email;

                        if (json.has("user")) {
                            JSONObject user = json.getJSONObject("user");
                            userId = user.optString("id", "");
                            userEmail = user.optString("email", email);
                        }

                        saveSession(accessToken, refreshToken, userId, userEmail);

                        mainHandler.post(() -> callback.onSuccess(accessToken));
                    } else {
                        String errorMsg = parseError(responseBody);
                        mainHandler.post(() -> callback.onError(errorMsg));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Login error", e);
                mainHandler.post(() -> callback.onError("Login failed: " + e.getMessage()));
            }
        });
    }

    public void logout() {
        prefs.edit()
                .remove(KEY_TOKEN)
                .remove(KEY_REFRESH_TOKEN)
                .remove(KEY_USER_ID)
                .remove(KEY_EMAIL)
                .apply();
    }

    public boolean isLoggedIn() {
        String token = getToken();
        return token != null && !token.isEmpty();
    }

    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public String getBearerToken() {
        String token = getToken();
        return token != null ? "Bearer " + token : null;
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    private void saveSession(String accessToken, String refreshToken, String userId, String email) {
        prefs.edit()
                .putString(KEY_TOKEN, accessToken)
                .putString(KEY_REFRESH_TOKEN, refreshToken)
                .putString(KEY_USER_ID, userId)
                .putString(KEY_EMAIL, email)
                .apply();
    }

    private String parseError(String responseBody) {
        try {
            JSONObject json = new JSONObject(responseBody);
            if (json.has("error_description")) {
                return json.getString("error_description");
            }
            if (json.has("msg")) {
                return json.getString("msg");
            }
            if (json.has("error")) {
                return json.getString("error");
            }
        } catch (Exception ignored) {
        }
        return "Invalid email or password";
    }
}