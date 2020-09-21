package com.ccbfm.proxy.droid;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ProxyDroidExecutor {

    public interface ProxyCallback{
        void callback(boolean isProxy);
    }

    private static final int MSG_CONNECT_START = 0;
    private static final int MSG_CONNECT_FINISH = 1;
    private static final int MSG_CONNECT_SUCCESS = 2;
    private static final int MSG_CONNECT_FAIL = 3;
    private static final int MSG_CONNECT_PAC_ERROR = 4;
    private static final int MSG_CONNECT_RESOLVE_ERROR = 5;

    private final static String CMD_IPTABLES_RETURN = "iptables -t nat -A OUTPUT -p tcp -d 0.0.0.0 -j RETURN\n";

    private final static String CMD_IPTABLES_REDIRECT_ADD_HTTP = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j REDIRECT --to 8123\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 443 -j REDIRECT --to 8124\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 5228 -j REDIRECT --to 8124\n";

    private final static String CMD_IPTABLES_DNAT_ADD_HTTP = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8124\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 5228 -j DNAT --to-destination 127.0.0.1:8124\n";

    private final static String CMD_IPTABLES_REDIRECT_ADD_HTTP_TUNNEL = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j REDIRECT --to 8123\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 443 -j REDIRECT --to 8123\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 5228 -j REDIRECT --to 8123\n";

    private final static String CMD_IPTABLES_DNAT_ADD_HTTP_TUNNEL = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to-destination 127.0.0.1:8123\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 443 -j DNAT --to-destination 127.0.0.1:8123\n"
            + "iptables -t nat -A OUTPUT -p tcp --dport 5228 -j DNAT --to-destination 127.0.0.1:8123\n";

    private final static String CMD_IPTABLES_REDIRECT_ADD_SOCKS = "iptables -t nat -A OUTPUT -p tcp -j REDIRECT --to 8123\n";

    private final static String CMD_IPTABLES_DNAT_ADD_SOCKS = "iptables -t nat -A OUTPUT -p tcp -j DNAT --to-destination 127.0.0.1:8123\n";

    private static final String TAG = "ProxyDroidExecutor";

    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private Context mContext;
    private SharedPreferences mSPSettings = null;
    private MessageHandler mHandler;
    private NotificationManager mNotificationManager;
    private ProxyCallback mProxyCallback;

    private static class MessageHandler extends Handler {
        private SharedPreferences mPreferences;
        private Context mContext;

        private MessageHandler(Looper looper, Context context, SharedPreferences sp) {
            super(looper);
            mPreferences = sp;
            mContext = context;
        }

        @Override
        public void handleMessage(Message msg) {
            SharedPreferences.Editor ed = mPreferences.edit();
            switch (msg.what) {
                case MSG_CONNECT_START:
                    ed.putBoolean("isConnecting", true);
                    Utils.setConnecting(true);
                    break;
                case MSG_CONNECT_FINISH:
                    ed.putBoolean("isConnecting", false);
                    Utils.setConnecting(false);
                    break;
                case MSG_CONNECT_SUCCESS:
                    ed.putBoolean("isRunning", true);
                    break;
                case MSG_CONNECT_FAIL:
                    ed.putBoolean("isRunning", false);
                    break;
                case MSG_CONNECT_PAC_ERROR:
                    Toast.makeText(mContext, R.string.msg_pac_error, Toast.LENGTH_SHORT)
                            .show();
                    break;
                case MSG_CONNECT_RESOLVE_ERROR:
                    Toast.makeText(mContext, R.string.msg_resolve_error,
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            ed.apply();
            super.handleMessage(msg);
        }
    }

    private void callbackResult(boolean isProxy){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                if(mProxyCallback != null){
                    mProxyCallback.callback(isProxy);
                }
            }
        });
    }

    public ProxyDroidExecutor(Context context) {
        mContext = context;
        basePath = context.getFilesDir().getAbsolutePath() + "/";
        mSPSettings = PreferenceManager.getDefaultSharedPreferences(context);
        mHandler = new MessageHandler(Looper.getMainLooper(), context, mSPSettings);
        mNotificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        createNotificationChannel();
    }

    public void setProxyCallback(ProxyCallback proxyCallback) {
        mProxyCallback = proxyCallback;
    }

    private void initParameters(Context context) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(context);
        Profile profile = new Profile();
        profile.getProfile(settings);

        host = profile.getHost();
        bypassAddrs = profile.getBypassAddrs();
        proxyType = profile.getProxyType();
        port = profile.getPort();
        isAutoSetProxy = profile.isAutoSetProxy();
        isBypassApps = profile.isBypassApps();
        isAuth = profile.isAuth();
        isNTLM = profile.isNTLM();

        if (isAuth) {
            auth = "true";
            user = profile.getUser();
            password = profile.getPassword();
        } else {
            auth = "false";
            user = "";
            password = "";
        }

        if (isNTLM) {
            domain = profile.getDomain();
        } else {
            domain = "";
        }
    }

    public void executeProxy() {
        initParameters(mContext);
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                mHandler.sendEmptyMessage(MSG_CONNECT_START);

                hasRedirectSupport = Utils.getHasRedirectSupport();

                if (getAddress() && handleCommand(mContext)) {
                    // Connection and forward successful
                    notifyAlert(mContext.getString(R.string.forward_success),
                            mContext.getString(R.string.service_running));

                    callbackResult(true);
                    mHandler.sendEmptyMessage(MSG_CONNECT_SUCCESS);
                } else {
                    mHandler.sendEmptyMessage(MSG_CONNECT_FAIL);
                    callbackResult(false);
                }
                mHandler.sendEmptyMessage(MSG_CONNECT_FINISH);
            }
        });
    }

    public void stopProxy() {
        Utils.setConnecting(true);
        mNotificationManager.cancelAll();
        // Make sure the connection is closed, important here
        disconnectProxy();

        SharedPreferences.Editor ed = mSPSettings.edit();
        ed.putBoolean("isRunning", false);
        ed.apply();

        try {
            mNotificationManager.cancel(1);
        } catch (Exception ignore) {
            // Nothing
        }
        Utils.setConnecting(false);

    }

    private void disconnectProxy() {
        mExecutorService.submit(new Runnable() {
            @Override
            public void run() {
                final StringBuilder sb = new StringBuilder();
                sb.append(Utils.getIptables()).append(" -t nat -F OUTPUT\n");

                if ("https".equals(proxyType)) {
                    sb.append("kill -9 `cat ");
                    sb.append(basePath);
                    sb.append("gost.pid`\n");
                }

                if (isAuth && isNTLM) {
                    sb.append("kill -9 `cat ");
                    sb.append(basePath);
                    sb.append("cntlm.pid`\n");
                }

                sb.append(basePath);
                sb.append("proxy.sh ");
                sb.append(basePath);
                sb.append(" stop\n");

                Utils.runRootCommand(sb.toString());
            }
        });

    }

    private String host;
    private String hostName;
    private int port;
    private String bypassAddrs = "";
    private String user;
    private String password;
    private String domain;
    private String proxyType = "http";
    private String auth = "false";
    private boolean isAuth = false;
    private boolean isNTLM = false;
    private String basePath = "";

    private boolean hasRedirectSupport = true;
    private boolean isAutoSetProxy = false;
    private boolean isBypassApps = false;

    private ProxyedApp[] apps;

    private boolean getAddress() {
        hostName = host;
        try {
            host = InetAddress.getByName(host).getHostAddress();
        } catch (UnknownHostException e) {
            host = hostName;
            mHandler.sendEmptyMessageDelayed(MSG_CONNECT_RESOLVE_ERROR, 3000);
            return false;
        }
        Log.d(TAG, "Proxy: " + host + "  Local Port: " + port);
        return true;
    }

    private boolean handleCommand(Context context) {
        String filePath = context.getFilesDir().getAbsolutePath();
        Utils.runRootCommand(
                "chmod 700 " + filePath + "/redsocks\n"
                        + "chmod 700 " + filePath + "/proxy.sh\n"
                        + "chmod 700 " + filePath + "/gost.sh\n"
                        + "chmod 700 " + filePath + "/cntlm\n"
                        + "chmod 700 " + filePath + "/gost\n");
        enableProxy(context);
        return true;
    }

    private void enableProxy(Context context) {

        String proxyHost = host;
        int proxyPort = port;

        try {
            if ("https".equals(proxyType)) {
                String src = "-L=http://127.0.0.1:8126";
                String auth = "";
                if (!user.isEmpty() && !password.isEmpty()) {
                    auth = user + ":" + password + "@";
                }
                String dst = "-F=https://" + auth + hostName + ":" + port + "?ip=" + host;

                // Start gost here
                Utils.runRootCommand(basePath + "gost.sh " + basePath + " " + src + " " + dst);

                // Reset host / port
                proxyHost = "127.0.0.1";
                proxyPort = 8126;
                proxyType = "http";
            }

            if (proxyType.equals("http") && isAuth && isNTLM) {
                Utils.runRootCommand(basePath + "proxy.sh " + basePath + " start http 127.0.0.1 8025 false\n"
                        + basePath + "cntlm -P " + basePath + "cntlm.pid -l 8025 -u " + user
                        + (!domain.equals("") ? "@" + domain : "@local") + " -p " + password + " "
                        + proxyHost + ":" + proxyPort + "\n");
            } else {
                final String u = Utils.preserve(user);
                final String p = Utils.preserve(password);

                Utils.runRootCommand(basePath + "proxy.sh " + basePath + " start" + " " + proxyType + " " + proxyHost
                        + " " + proxyPort + " " + auth + " \"" + u + "\" \"" + p + "\"");
            }

            StringBuilder cmd = new StringBuilder();

            cmd.append(CMD_IPTABLES_RETURN.replace("0.0.0.0", host));

            if (bypassAddrs != null && !bypassAddrs.equals("")) {
                String[] addrs = Profile.decodeAddrs(bypassAddrs);
                for (String addr : addrs)
                    cmd.append(CMD_IPTABLES_RETURN.replace("0.0.0.0", addr));
            }

            String redirectCmd = CMD_IPTABLES_REDIRECT_ADD_HTTP;
            String dnatCmd = CMD_IPTABLES_DNAT_ADD_HTTP;

            if (proxyType.equals("socks4") || proxyType.equals("socks5")) {
                redirectCmd = CMD_IPTABLES_REDIRECT_ADD_SOCKS;
                dnatCmd = CMD_IPTABLES_DNAT_ADD_SOCKS;
            } else if (proxyType.equals("http-tunnel")) {
                redirectCmd = CMD_IPTABLES_REDIRECT_ADD_HTTP_TUNNEL;
                dnatCmd = CMD_IPTABLES_DNAT_ADD_HTTP_TUNNEL;
            }

            if (isBypassApps) {
                // for host specified apps
                if (apps == null || apps.length <= 0)
                    apps = AppManager.getProxyedApps(context, false);

                for (ProxyedApp app : apps) {
                    if (app != null) {
                        Log.w("wds", "app1=" + app.getName() + "," + app.isProxyed());
                    }
                    if (app != null && app.isProxyed()) {
                        cmd.append(CMD_IPTABLES_RETURN.replace("-d 0.0.0.0", "").replace("-t nat",
                                "-t nat -m owner --uid-owner " + app.getUid()));
                    }
                }

            }

            if (isAutoSetProxy || isBypassApps) {
                cmd.append(hasRedirectSupport ? redirectCmd : dnatCmd);
            } else {
                // for host specified apps
                if (apps == null || apps.length <= 0)
                    apps = AppManager.getProxyedApps(context, true);

                for (ProxyedApp app : apps) {
                    if (app != null) {
                        Log.w("wds", "app2=" + app.getName() + "," + app.isProxyed());
                    }
                    if (app != null && app.isProxyed()) {
                        cmd.append((hasRedirectSupport ? redirectCmd : dnatCmd).replace("-t nat",
                                "-t nat -m owner --uid-owner " + app.getUid()));
                    }
                }
            }

            String rules = cmd.toString();

            rules = rules.replace("iptables", Utils.getIptables());

            Utils.runRootCommand(rules);

        } catch (Exception e) {
            Log.e(TAG, "Error setting up port forward during connect", e);
        }

    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "ProxyDroid Service";
            String description = "ProxyDroid Background Service";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel("Service", name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            mNotificationManager.createNotificationChannel(channel);
        }
    }

    private void notifyAlert(String title, String info) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(mContext, "Service");

        initSoundVibrateLights(builder);

        builder.setAutoCancel(false);
        builder.setTicker(title);
        builder.setContentTitle(mContext.getString(R.string.app_name));
        builder.setContentText(info);
        builder.setSmallIcon(R.drawable.ic_android_black_24dp);
        //builder.setContentIntent(pendIntent);
        builder.setPriority(NotificationCompat.PRIORITY_LOW);
        builder.setOngoing(true);
        mNotificationManager.notify(1, builder.build());
    }

    private void initSoundVibrateLights(NotificationCompat.Builder builder) {
        final String ringtone = mSPSettings.getString("settings_key_notif_ringtone", null);
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        if (audioManager.getStreamVolume(AudioManager.STREAM_RING) == 0) {
            builder.setSound(null);
        } else if (ringtone != null) {
            builder.setSound(Uri.parse(ringtone));
        }

        if (mSPSettings.getBoolean("settings_key_notif_vibrate", false)) {
            builder.setVibrate(new long[]{0, 1000, 500, 1000, 500, 1000});
        }
    }
}
