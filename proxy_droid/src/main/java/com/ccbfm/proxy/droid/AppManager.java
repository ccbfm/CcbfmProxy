package com.ccbfm.proxy.droid;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;

import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Vector;

public class AppManager {

    public final static String PREFS_KEY_PROXYED = "Proxyed_APPS";
    private ProxyedApp[] apps = null;

    public static ProxyedApp[] getProxyedApps(Context context, boolean self) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String tordAppString = prefs.getString(PREFS_KEY_PROXYED, "");
        String[] tordApps;

        StringTokenizer st = new StringTokenizer(tordAppString, "|");
        tordApps = new String[st.countTokens()];
        int tordIdx = 0;
        while (st.hasMoreTokens()) {
            tordApps[tordIdx++] = st.nextToken();
        }

        Arrays.sort(tordApps);

        // else load the apps up
        PackageManager pMgr = context.getPackageManager();

        List<ApplicationInfo> lAppInfo = pMgr.getInstalledApplications(0);

        Iterator<ApplicationInfo> itAppInfo = lAppInfo.iterator();

        Vector<ProxyedApp> vectorApps = new Vector<ProxyedApp>();

        ApplicationInfo aInfo = null;

        int appIdx = 0;
        String selfPackageName = context.getPackageName();
        while (itAppInfo.hasNext()) {
            aInfo = itAppInfo.next();

            // ignore all system apps
            if (aInfo.uid < 10000)
                continue;

            ProxyedApp app = new ProxyedApp();

            app.setUid(aInfo.uid);

            app.setUsername(pMgr.getNameForUid(app.getUid()));

            // check if this application is allowed
            if (aInfo.packageName != null
                    && aInfo.packageName.equals(selfPackageName)) {
                if (self) {
                    app.setProxyed(true);
                }
            } else if (Arrays.binarySearch(tordApps, app.getUsername()) >= 0) {
                app.setProxyed(true);
            } else {
                app.setProxyed(false);
            }

            if (app.isProxyed()) {
                vectorApps.add(app);
            }

        }

        ProxyedApp[] apps = new ProxyedApp[vectorApps.size()];
        vectorApps.toArray(apps);
        return apps;
    }

    public void getApps(Context context) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String tordAppString = prefs.getString(PREFS_KEY_PROXYED, "");
        String[] tordApps;

        StringTokenizer st = new StringTokenizer(tordAppString, "|");
        tordApps = new String[st.countTokens()];
        int tordIdx = 0;
        while (st.hasMoreTokens()) {
            tordApps[tordIdx++] = st.nextToken();
        }

        Arrays.sort(tordApps);

        Vector<ProxyedApp> vectorApps = new Vector<ProxyedApp>();

        // else load the apps up
        PackageManager pMgr = context.getPackageManager();

        List<ApplicationInfo> lAppInfo = pMgr.getInstalledApplications(0);

        Iterator<ApplicationInfo> itAppInfo = lAppInfo.iterator();

        ApplicationInfo aInfo = null;

        while (itAppInfo.hasNext()) {
            aInfo = itAppInfo.next();

            // ignore system apps
            if (aInfo.uid < 10000)
                continue;

            if (aInfo.processName == null)
                continue;
            if (pMgr.getApplicationLabel(aInfo) == null
                    || pMgr.getApplicationLabel(aInfo).toString().equals(""))
                continue;
            if (pMgr.getApplicationIcon(aInfo) == null)
                continue;

            ProxyedApp tApp = new ProxyedApp();

            tApp.setEnabled(aInfo.enabled);
            tApp.setUid(aInfo.uid);
            tApp.setUsername(pMgr.getNameForUid(tApp.getUid()));
            tApp.setProcname(aInfo.processName);
            tApp.setName(pMgr.getApplicationLabel(aInfo).toString());

            // check if this application is allowed
            if (Arrays.binarySearch(tordApps, tApp.getUsername()) >= 0) {
                tApp.setProxyed(true);
            } else {
                tApp.setProxyed(false);
            }

            vectorApps.add(tApp);
        }

        apps = new ProxyedApp[vectorApps.size()];
        vectorApps.toArray(apps);

    }

    public void saveAppSettings(Context context) {
        if (apps == null)
            return;

        SharedPreferences prefs = PreferenceManager
                .getDefaultSharedPreferences(context);

        // final SharedPreferences prefs =
        // context.getSharedPreferences(PREFS_KEY, 0);

        StringBuilder tordApps = new StringBuilder();

        for (int i = 0; i < apps.length; i++) {
            if (apps[i].isProxyed()) {
                tordApps.append(apps[i].getUsername());
                tordApps.append("|");
            }
        }

        SharedPreferences.Editor edit = prefs.edit();
        edit.putString(PREFS_KEY_PROXYED, tordApps.toString());
        edit.apply();

    }
}
