package com.home.weatherstation;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Created by thaarres on 19/06/16.
 */
public class AuthPreferences {

    private static final String KEY_USER = "user";

    private final SharedPreferences preferences;

    public AuthPreferences(Context context) {
        preferences = context.getSharedPreferences("auth", Context.MODE_PRIVATE);
    }

    @SuppressLint("CommitPrefEdits")
    public void setUser(String user) {
        SharedPreferences.Editor editor = preferences.edit();
        editor.putString(KEY_USER, user);
        editor.commit();
    }


    public String getUser() {
        return preferences.getString(KEY_USER, null);
    }

}
