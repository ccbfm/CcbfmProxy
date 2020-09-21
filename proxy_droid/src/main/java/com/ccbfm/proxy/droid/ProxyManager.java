package com.ccbfm.proxy.droid;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class ProxyManager {
    private static final String TAG = "ProxyManager";
    private volatile boolean mInit = false;
    private static final String PROXY_INIT = "proxy_init";

    private ProxyManager() {
    }

    private static class Singleton {
        private static final ProxyManager INSTANCE = new ProxyManager();
    }

    public static ProxyManager instance() {
        return Singleton.INSTANCE;
    }

    public void init(final Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        boolean init = settings.getBoolean(PROXY_INIT, false);
        if (init) {
            mInit = true;
            return;
        }
        new Thread(new Runnable() {
            @Override
            public void run() {
                resetAndLoadProxy(context);
            }
        }).start();
    }

    private ProxyDroidExecutor mProxyDroidExecutor;

    public boolean startProxy(Context context, String host, int port, ProxyDroidExecutor.ProxyCallback callback) {
        Log.e(TAG, "startProxy---mInit= " + mInit);
        if (!mInit) {
            Toast.makeText(context, "初始化未完成，请稍后！", Toast.LENGTH_LONG).show();
            return false;
        }
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        Profile profile = new Profile();
        profile.getProfile(settings);
        profile.setHost(host);
        profile.setPort(port);
        profile.setAutoSetProxy(true);
        profile.setProxyType("socks5");
        profile.setProfile(settings);
        if(mProxyDroidExecutor == null) {
            mProxyDroidExecutor = new ProxyDroidExecutor(context);
        }
        mProxyDroidExecutor.setProxyCallback(callback);
        mProxyDroidExecutor.executeProxy();
        return true;
    }

    public boolean stopProxy() {
        if (mProxyDroidExecutor != null) {
            mProxyDroidExecutor.stopProxy();
            return true;
        }
        return false;
    }

    public void resetProxy(Context context) {
        String filePath = context.getFilesDir().getAbsolutePath();
        Utils.runRootCommand(Utils.getIptables()
                + " -t nat -F OUTPUT\n"
                + context.getFilesDir().getAbsolutePath()
                + "/proxy.sh stop\n"
                + "kill -9 `cat " + filePath + "cntlm.pid`\n");
    }

    private void resetAndLoadProxy(Context context) {
        try {
            context.stopService(new Intent(context, ProxyDroidService.class));
        } catch (Exception e) {
            // Nothing
        }

        copyAssets(context);

        String filePath = context.getFilesDir().getAbsolutePath();

        Utils.runRootCommand(Utils.getIptables()
                + " -t nat -F OUTPUT\n"
                + context.getFilesDir().getAbsolutePath()
                + "/proxy.sh stop\n"
                + "kill -9 `cat " + filePath + "cntlm.pid`\n");

        Utils.runRootCommand(
                "chmod 700 " + filePath + "/redsocks\n"
                        + "chmod 700 " + filePath + "/proxy.sh\n"
                        + "chmod 700 " + filePath + "/gost.sh\n"
                        + "chmod 700 " + filePath + "/cntlm\n"
                        + "chmod 700 " + filePath + "/gost\n");
        Log.e(TAG, "初始化完成----");
        mInit = true;
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        settings.edit().putBoolean(PROXY_INIT, true).apply();
    }

    private void copyAssets(Context context) {
        AssetManager assetManager = context.getAssets();
        String[] files = null;
        String abi = null;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.SUPPORTED_ABIS[0];
        } else {
            abi = Build.CPU_ABI;
        }
        try {
            if (abi.matches("armeabi-v7a|arm64-v8a"))
                files = assetManager.list("armeabi-v7a");
            else
                files = assetManager.list("x86");
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        if (files != null) {
            for (String file : files) {
                InputStream in = null;
                OutputStream out = null;
                try {
                    if (abi.matches("armeabi-v7a|arm64-v8a"))
                        in = assetManager.open("armeabi-v7a/" + file);
                    else
                        in = assetManager.open("x86/" + file);
                    out = new FileOutputStream(context.getFilesDir().getAbsolutePath() + "/" + file);
                    copyFile(in, out);
                    in.close();
                    in = null;
                    out.flush();
                    out.close();
                    out = null;
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }
}
