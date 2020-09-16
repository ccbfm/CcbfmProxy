package com.ccbfm.proxy;

import android.app.Application;

import com.ccbfm.proxy.droid.ProxyManager;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        ProxyManager.instance().init(this);
    }
}
