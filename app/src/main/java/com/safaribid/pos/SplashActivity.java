package com.safaribid.pos;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.view.WindowCompat;

import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.auth.LoginActivity;
import com.safaribid.pos.network.SocketManager;
import com.safaribid.pos.ui.orders.OrdersActivity;

@SuppressLint("CustomSplashScreen")
public class SplashActivity extends AppCompatActivity {

    private static final long SPLASH_MS = 1200L;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        WindowCompat.setDecorFitsSystemWindows(getWindow(), true);
        getWindow().setStatusBarColor(ContextCompat.getColor(this, R.color.brand_green));
        getWindow().setNavigationBarColor(ContextCompat.getColor(this, R.color.brand_green));
        WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView())
                .setAppearanceLightStatusBars(false);

        new Handler(Looper.getMainLooper()).postDelayed(this::goNext, SPLASH_MS);
    }

    private void goNext() {
        if (isFinishing()) return;

        AuthManager auth = new AuthManager(this);
        Intent intent;

        if (auth.isLoggedIn()) {
            String uid = auth.getUserId();
            if (uid != null && !uid.isEmpty()) {
                SocketManager.getInstance().connect(uid);
            }
            intent = new Intent(this, OrdersActivity.class);
        } else {
            intent = new Intent(this, LoginActivity.class);
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
