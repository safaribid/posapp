package com.example.pos_app;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.pos_app.auth.AuthManager;
import com.example.pos_app.auth.LoginActivity;
import com.example.pos_app.network.SocketManager;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnLogout;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authManager = new AuthManager(this);

        // Safety check – if not logged in, go back to Login
        if (!authManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        tvStatus = findViewById(R.id.tv_status);
        btnLogout = findViewById(R.id.btn_logout);

        String email = authManager.getEmail();
        if (email != null && !email.isEmpty()) {
            tvStatus.setText("Welcome to SafariBid POS\nLogged in as: " + email);
        } else {
            tvStatus.setText("Welcome to SafariBid POS\nYou are logged in.");
        }

        // Connect socket with the real token
        String token = authManager.getToken();
        if (token != null && !token.isEmpty()) {
            SocketManager.getInstance().connect(token);
        }

        btnLogout.setOnClickListener(v -> logout());
    }

    private void logout() {
        // Disconnect socket
        SocketManager.getInstance().disconnect();

        // Clear session
        authManager.logout();

        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();
        goToLogin();
    }

    private void goToLogin() {
        Intent intent = new Intent(MainActivity.this, LoginActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Optional: disconnect socket when activity is destroyed
        // SocketManager.getInstance().disconnect();
    }
}