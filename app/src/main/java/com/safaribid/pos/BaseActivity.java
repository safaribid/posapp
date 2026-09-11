package com.safaribid.pos;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

public abstract class BaseActivity extends AppCompatActivity {
    protected void applySystemBarInsets(View top, View bottom) {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        if (top != null) {
            ViewCompat.setOnApplyWindowInsetsListener(top, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.statusBars());
                v.setPadding(v.getPaddingLeft(), bars.top, v.getPaddingRight(), v.getPaddingBottom());
                return insets;
            });
        }
        if (bottom != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottom, (v, insets) -> {
                Insets bars = insets.getInsets(WindowInsetsCompat.Type.navigationBars());
                v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), bars.bottom);
                return insets;
            });
        }
    }
}