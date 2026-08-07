package com.safaribid.pos.auth;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.safaribid.pos.utils.AppConfig;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

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

    private static final String KEY_USER_JSON = "user_json";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_EMAIL = "email";
    private static final String KEY_FNAME = "fname";
    private static final String KEY_ACCESS_TOKEN = "access_token";
    private static final String KEY_REFRESH_TOKEN = "refresh_token";

    private final SharedPreferences prefs;
    private final OkHttpClient client;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final Context context;

    public interface AuthCallback {
        void onSuccess(JSONObject userData);
        void onError(String message);
    }

    public AuthManager(Context context) {
        this.context = context.getApplicationContext();
        prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        client = new OkHttpClient();
        executor = Executors.newSingleThreadExecutor();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    // ========== EMAIL + PASSWORD ==========
    public void loginWithEmail(String email, String password, AuthCallback callback) {
        getFirebaseToken(firebaseToken -> {
            JSONObject body = new JSONObject();
            try {
                body.put("email", email);
                body.put("password", password);
                if (firebaseToken != null) {
                    body.put("firebaseToken", firebaseToken);
                }
            } catch (Exception e) {
                callback.onError("Invalid request");
                return;
            }
            performApiLogin("/auth/signin-email", body, email, password, callback);
        });
    }

    // ========== PHONE + PASSWORD ==========
    public void loginWithPhone(String phone, String password, AuthCallback callback) {
        String normalizedPhone = normalizeKenyanPhone(phone);

        if (normalizedPhone == null) {
            callback.onError("Invalid phone number. Use +2547..., 07... or 01...");
            return;
        }

        getFirebaseToken(firebaseToken -> {
            JSONObject body = new JSONObject();
            try {
                body.put("phone", normalizedPhone);
                body.put("password", password);
                if (firebaseToken != null) {
                    body.put("firebaseToken", firebaseToken);
                }
            } catch (Exception e) {
                callback.onError("Invalid request");
                return;
            }
            // For phone we still need email or phone for Supabase step.
            // We pass the normalized phone; Supabase phone login uses the phone field.
            performApiLogin("/auth/signin-phone", body, normalizedPhone, password, callback, true);
        });
    }

    private void performApiLogin(String endpoint, JSONObject body, String identifier,
                                 String password, AuthCallback callback) {
        performApiLogin(endpoint, body, identifier, password, callback, false);
    }

    private void performApiLogin(String endpoint, JSONObject body, String identifier,
                                 String password, AuthCallback callback, boolean isPhone) {
        executor.execute(() -> {
            try {
                RequestBody requestBody = RequestBody.create(
                        body.toString(),
                        MediaType.parse("application/json")
                );

                String baseUrl = AppConfig.SERVER_API;
                if (baseUrl.endsWith("/")) {
                    baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                }
                String url = baseUrl + endpoint;

                Request request = new Request.Builder()
                        .url(url)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("Accept", "application/json")
                        .post(requestBody)
                        .build();

                try (Response response = client.newCall(request).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    Log.d(TAG, "API Login response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    boolean success = json.optBoolean("success", false);

                    if (response.isSuccessful() && success) {
                        JSONObject userData = json.optJSONObject("data");
                        if (userData != null) {
                            // Step 2: Get Supabase session token
                            obtainSupabaseSession(identifier, password, isPhone, userData, callback);
                        } else {
                            mainHandler.post(() -> callback.onError("No user data received"));
                        }
                    } else {
                        String message = "Login failed";
                        JSONObject info = json.optJSONObject("info");
                        if (info != null) {
                            message = info.optString("message", message);
                        }
                        String finalMessage = message;
                        mainHandler.post(() -> callback.onError(finalMessage));
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "API Login error", e);
                mainHandler.post(() -> callback.onError("Login failed: " + e.getMessage()));
            }
        });
    }

    /**
     * Step 2: Sign in directly to Supabase to obtain the session token.
     * This token is required as Bearer token for all subsequent API calls.
     */
    private void obtainSupabaseSession(String identifier, String password, boolean isPhone,
                                       JSONObject userData, AuthCallback callback) {
        try {
            JSONObject body = new JSONObject();
            if (isPhone) {
                body.put("phone", identifier);
            } else {
                body.put("email", identifier);
            }
            body.put("password", password);

            RequestBody requestBody = RequestBody.create(
                    body.toString(),
                    MediaType.parse("application/json")
            );

            String url = AppConfig.SUPABASE_URL + "/auth/v1/token?grant_type=password";

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("apikey", AppConfig.SUPABASE_KEY)
                    .addHeader("Content-Type", "application/json")
                    .post(requestBody)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "Supabase session response: " + responseBody);

                if (response.isSuccessful()) {
                    JSONObject json = new JSONObject(responseBody);
                    String accessToken = json.optString("access_token", "");
                    String refreshToken = json.optString("refresh_token", "");

                    if (accessToken.isEmpty()) {
                        mainHandler.post(() -> callback.onError("Failed to obtain session token"));
                        return;
                    }

                    // Save both user data and session tokens
                    saveSession(userData, accessToken, refreshToken);
                    mainHandler.post(() -> callback.onSuccess(userData));
                } else {
                    mainHandler.post(() -> callback.onError("Failed to get Supabase session token"));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Supabase session error", e);
            mainHandler.post(() -> callback.onError("Session error: " + e.getMessage()));
        }
    }

    private String normalizeKenyanPhone(String phone) {
        if (phone == null) return null;

        String cleaned = phone.replaceAll("[\\s\\-()]", "");

        if (cleaned.startsWith("+254") && cleaned.length() == 13) {
            return cleaned;
        }
        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            return "+254" + cleaned.substring(1);
        }
        if (cleaned.startsWith("254") && cleaned.length() == 12) {
            return "+" + cleaned;
        }
        return null;
    }

    private void getFirebaseToken(FirebaseTokenCallback callback) {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null) {
                        callback.onToken(task.getResult());
                    } else {
                        callback.onToken(null);
                    }
                });
    }

    private interface FirebaseTokenCallback {
        void onToken(String token);
    }

    private void saveSession(JSONObject userData, String accessToken, String refreshToken) {
        try {
            prefs.edit()
                    .putString(KEY_USER_JSON, userData.toString())
                    .putString(KEY_USER_ID, userData.optString("id", ""))
                    .putString(KEY_EMAIL, userData.optString("email", ""))
                    .putString(KEY_FNAME, userData.optString("fname", ""))
                    .putString(KEY_ACCESS_TOKEN, accessToken)
                    .putString(KEY_REFRESH_TOKEN, refreshToken)
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save session", e);
        }
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        String token = getAccessToken();
        String userJson = prefs.getString(KEY_USER_JSON, null);
        return token != null && !token.isEmpty() && userJson != null && !userJson.isEmpty();
    }

    public String getAccessToken() {
        return prefs.getString(KEY_ACCESS_TOKEN, null);
    }

    public String getBearerToken() {
        String token = getAccessToken();
        return token != null ? "Bearer " + token : null;
    }

    public String getUserId() {
        return prefs.getString(KEY_USER_ID, null);
    }

    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    public String getFirstName() {
        return prefs.getString(KEY_FNAME, null);
    }

    public JSONObject getUser() {
        try {
            String json = prefs.getString(KEY_USER_JSON, null);
            if (json != null) {
                return new JSONObject(json);
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}