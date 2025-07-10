package com.project.realtimechatui;

import android.content.Context;

import com.project.realtimechatui.utils.SharedPrefManager;

public class Application extends android.app.Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize SharedPrefManager
        SharedPrefManager.getInstance(this);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }
}