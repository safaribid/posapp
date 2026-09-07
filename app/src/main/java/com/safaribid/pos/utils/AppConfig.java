package com.safaribid.pos.utils;

import com.safaribid.pos.BuildConfig;

public class AppConfig {
    public static final String SUPABASE_URL = BuildConfig.SUPABASE_URL;
    public static final String SUPABASE_KEY = BuildConfig.SUPABASE_PUB_KEY;
    public static final String SERVER_API = BuildConfig.SERVER_API;

    public static final String SHOP_NAME = "SafariBid";
    public static final String SHOP_ADDRESS = "Nairobi, Kenya";
    public static final String SHOP_PHONE = "+254 700 000 000";

    /** Origin only, e.g. https://api.safaribid.com */
    public static String serverOrigin() {
        String url = SERVER_API != null ? SERVER_API.trim() : "";
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/api/pos")) {
            url = url.substring(0, url.length() - "/api/pos".length());
        } else if (url.endsWith("/api")) {
            url = url.substring(0, url.length() - "/api".length());
        }
        return url.isEmpty() ? "https://api.safaribid.com" : url;
    }
}