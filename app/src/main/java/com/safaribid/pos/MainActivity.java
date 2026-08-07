package com.safaribid.pos;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.safaribid.pos.auth.AuthManager;
import com.safaribid.pos.auth.LoginActivity;
import com.safaribid.pos.network.SocketManager;
import com.safaribid.pos.ui.orders.OrdersActivity;

public class MainActivity extends AppCompatActivity {

    private TextView tvStatus;
    private Button btnOrders;
    private Button btnLogout;
    private AuthManager authManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        authManager = new AuthManager(this);

        if (!authManager.isLoggedIn()) {
            goToLogin();
            return;
        }

        tvStatus = findViewById(R.id.tv_status);
        btnOrders = findViewById(R.id.btn_orders);
        btnLogout = findViewById(R.id.btn_logout);

        String firstName = authManager.getFirstName();
        String email = authManager.getEmail();

        if (firstName != null && !firstName.isEmpty()) {
            tvStatus.setText("Welcome to SafariBid POS\nHello, " + firstName);
        } else if (email != null && !email.isEmpty()) {
            tvStatus.setText("Welcome to SafariBid POS\nLogged in as: " + email);
        } else {
            tvStatus.setText("Welcome to SafariBid POS\nYou are logged in.");
        }

        String userId = authManager.getUserId();
        if (userId != null && !userId.isEmpty()) {
            SocketManager.getInstance().connect(userId);
        }

        btnOrders.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, OrdersActivity.class);
            startActivity(intent);
        });

        btnLogout.setOnClickListener(v -> logout());
    }

    private void logout() {
        SocketManager.getInstance().disconnect();
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
}