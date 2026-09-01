package com.safaribid.pos.auth;

import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.safaribid.pos.ui.orders.OrdersActivity;
import com.safaribid.pos.R;
import com.safaribid.pos.network.SocketManager;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

public class LoginActivity extends AppCompatActivity {

    private LinearLayout layoutChoices;
    private LinearLayout layoutEmailForm;
    private LinearLayout layoutPhoneForm;

    private TextInputEditText etEmail;
    private TextInputEditText etPasswordEmail;
    private TextInputEditText etPhone;
    private TextInputEditText etPasswordPhone;

    private Button btnShowEmail;
    private Button btnShowPhone;
    private Button btnLoginEmail;
    private Button btnLoginPhone;
    private Button btnBackFromEmail;
    private Button btnBackFromPhone;

    private ProgressBar progressBar;
    private TextView tvError;

    private AuthManager authManager;

    private final AuthManager.AuthCallback authCallback = new AuthManager.AuthCallback() {
        @Override
        public void onSuccess(JSONObject userData) {
            setLoading(false);
            android.util.Log.d("LoginActivity", "User Data: " + userData.toString());
            String name = userData.optString("fname", "User");
            Toast.makeText(LoginActivity.this, "Welcome back " + name, Toast.LENGTH_SHORT).show();

            // AuthCallback onSuccess — after login succeeds
            String uid = userData.optString("id", "");
            if (!uid.isEmpty()) {
                SocketManager.getInstance().connect(uid);
            }
            goToOrders();
        }

        @Override
        public void onError(String message) {
            setLoading(false);
            showError(message);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        authManager = new AuthManager(this);

        if (authManager.isLoggedIn()) {
            String uid = authManager.getUserId();
            if (uid != null && !uid.isEmpty()) {
                SocketManager.getInstance().connect(uid);
            }
            goToOrders();
            return;
        }

        // Layouts
        layoutChoices = findViewById(R.id.layoutChoices);
        layoutEmailForm = findViewById(R.id.layoutEmailForm);
        layoutPhoneForm = findViewById(R.id.layoutPhoneForm);

        // Fields
        etEmail = findViewById(R.id.etEmail);
        etPasswordEmail = findViewById(R.id.etPasswordEmail);
        etPhone = findViewById(R.id.etPhone);
        etPasswordPhone = findViewById(R.id.etPasswordPhone);

        // Buttons
        btnShowEmail = findViewById(R.id.btnShowEmail);
        btnShowPhone = findViewById(R.id.btnShowPhone);
        btnLoginEmail = findViewById(R.id.btnLoginEmail);
        btnLoginPhone = findViewById(R.id.btnLoginPhone);
        btnBackFromEmail = findViewById(R.id.btnBackFromEmail);
        btnBackFromPhone = findViewById(R.id.btnBackFromPhone);

        progressBar = findViewById(R.id.progressBar);
        tvError = findViewById(R.id.tvError);

        // Click listeners
        btnShowEmail.setOnClickListener(v -> showEmailForm());
        btnShowPhone.setOnClickListener(v -> showPhoneForm());
        btnBackFromEmail.setOnClickListener(v -> showChoices());
        btnBackFromPhone.setOnClickListener(v -> showChoices());

        btnLoginEmail.setOnClickListener(v -> attemptEmailLogin());
        btnLoginPhone.setOnClickListener(v -> attemptPhoneLogin());
    }

    private void showChoices() {
        layoutChoices.setVisibility(View.VISIBLE);
        layoutEmailForm.setVisibility(View.GONE);
        layoutPhoneForm.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showEmailForm() {
        layoutChoices.setVisibility(View.GONE);
        layoutEmailForm.setVisibility(View.VISIBLE);
        layoutPhoneForm.setVisibility(View.GONE);
        tvError.setVisibility(View.GONE);
    }

    private void showPhoneForm() {
        layoutChoices.setVisibility(View.GONE);
        layoutEmailForm.setVisibility(View.GONE);
        layoutPhoneForm.setVisibility(View.VISIBLE);
        tvError.setVisibility(View.GONE);
    }

    private void attemptEmailLogin() {
        String email = getText(etEmail);
        String password = getText(etPasswordEmail);

        tvError.setVisibility(View.GONE);

        if (TextUtils.isEmpty(email)) {
            showError("Please enter your email");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password");
            return;
        }

        setLoading(true);
        authManager.loginWithEmail(email, password, authCallback);
    }

    private void attemptPhoneLogin() {
        String phone = getText(etPhone);
        String password = getText(etPasswordPhone);

        tvError.setVisibility(View.GONE);

        if (TextUtils.isEmpty(phone)) {
            showError("Please enter your phone number");
            return;
        }
        if (TextUtils.isEmpty(password)) {
            showError("Please enter your password");
            return;
        }

        setLoading(true);
        authManager.loginWithPhone(phone, password, authCallback);
    }

    private String getText(TextInputEditText editText) {
        return editText.getText() != null ? editText.getText().toString().trim() : "";
    }

    private void setLoading(boolean loading) {
        progressBar.setVisibility(loading ? View.VISIBLE : View.GONE);
        btnLoginEmail.setEnabled(!loading);
        btnLoginPhone.setEnabled(!loading);
        btnShowEmail.setEnabled(!loading);
        btnShowPhone.setEnabled(!loading);
    }

    private void showError(String message) {
        tvError.setText(message);
        tvError.setVisibility(View.VISIBLE);
    }

    private void goToOrders() {
        Intent intent = new Intent(LoginActivity.this, OrdersActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
