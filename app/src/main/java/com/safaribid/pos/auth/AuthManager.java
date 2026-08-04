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
            performLogin("/auth/signin-email", body, callback);
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
            performLogin("/auth/signin-phone", body, callback);
        });
    }

    // ========== GOOGLE ==========
    public void loginWithGoogle(String idToken, AuthCallback callback) {
        getFirebaseToken(firebaseToken -> {
            JSONObject body = new JSONObject();
            try {
                body.put("id_token", idToken);
                if (firebaseToken != null) {
                    body.put("firebaseToken", firebaseToken);
                }
            } catch (Exception e) {
                callback.onError("Invalid request");
                return;
            }
            performLogin("/auth/signin-google", body, callback);
        });
    }

    /**
     * Accepts:
     * - +2547xxxxxxxx
     * - 07xxxxxxxx
     * - 01xxxxxxxx
     * Returns normalized +254... or null if invalid
     */
    private String normalizeKenyanPhone(String phone) {
        if (phone == null) return null;

        // Remove spaces, dashes, brackets
        String cleaned = phone.replaceAll("[\\s\\-()]", "");

        if (cleaned.startsWith("+254") && cleaned.length() == 13) {
            return cleaned;
        }

        if (cleaned.startsWith("0") && cleaned.length() == 10) {
            // 07xxxxxxxx or 01xxxxxxxx → +2547... / +2541...
            return "+254" + cleaned.substring(1);
        }

        if (cleaned.startsWith("254") && cleaned.length() == 12) {
            return "+" + cleaned;
        }

        return null;
    }

    private void performLogin(String endpoint, JSONObject body, AuthCallback callback) {
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
                    Log.d(TAG, "Login response: " + responseBody);

                    JSONObject json = new JSONObject(responseBody);
                    boolean success = json.optBoolean("success", false);

                    if (response.isSuccessful() && success) {
                        JSONObject userData = json.optJSONObject("data");
                        if (userData != null) {
                            saveUser(userData);
                            mainHandler.post(() -> callback.onSuccess(userData));
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
                Log.e(TAG, "Login error", e);
                mainHandler.post(() -> callback.onError("Login failed: " + e.getMessage()));
            }
        });
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

    private void saveUser(JSONObject userData) {
        try {
            prefs.edit()
                    .putString(KEY_USER_JSON, userData.toString())
                    .putString(KEY_USER_ID, userData.optString("id", ""))
                    .putString(KEY_EMAIL, userData.optString("email", ""))
                    .putString(KEY_FNAME, userData.optString("fname", ""))
                    .apply();
        } catch (Exception e) {
            Log.e(TAG, "Failed to save user", e);
        }
    }

    public void logout() {
        prefs.edit().clear().apply();
    }

    public boolean isLoggedIn() {
        String userJson = prefs.getString(KEY_USER_JSON, null);
        return userJson != null && !userJson.isEmpty();
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