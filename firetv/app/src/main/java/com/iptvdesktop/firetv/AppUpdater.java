package com.iptvdesktop.firetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.net.Uri;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

final class AppUpdater {
    private final Activity activity;
    private final ExecutorService worker;
    private final Handler handler;
    private final SharedPreferences preferences;
    private boolean active,busy,cancelled;
    private volatile boolean closed;
    private Future<?> job;
    private int operation;
    private AlertDialog dialog;
    private File downloaded;
    private UpdateInfo downloadedInfo;
    AppUpdater(Activity activity,ExecutorService worker,Handler handler) {
        this.activity=activity;this.worker=worker;this.handler=handler;
        preferences=activity.getSharedPreferences("updates",Activity.MODE_PRIVATE);
    }
    void active(boolean value) {
        active=value;
        if(value) {
            long now=System.currentTimeMillis(),last=preferences.getLong("lastSuccessfulCheck",0);
            if(now<last||now-last>=6*60*60*1000L)check(false);
        }
    }
    private boolean visible() {return active&&!closed&&!activity.isFinishing();}
    private String version() {
        try{return activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionName;}
        catch(Exception ignored){return "installed version";}
    }
    void check(boolean manual) {
        if(closed)return;
        if(busy){if(manual)Toast.makeText(activity,"An update check or download is already running.",Toast.LENGTH_SHORT).show();return;}
        if(manual&&downloaded!=null&&downloaded.exists()){install();return;}
        busy=true;final int request=++operation;
        if(manual)Toast.makeText(activity,"Checking Fire TV updates…",Toast.LENGTH_SHORT).show();
        job=worker.submit(()->{
            try {
                UpdateInfo info=UpdateService.check();
                int installed=activity.getPackageManager().getPackageInfo(activity.getPackageName(),0).versionCode;
                handler.post(()->{
                    if(closed||request!=operation)return;busy=false;
                    preferences.edit().putLong("lastSuccessfulCheck",System.currentTimeMillis()).apply();
                    if(!visible())return;
                    if(info!=null&&info.versionCode>installed) {
                        dialog=new AlertDialog.Builder(activity).setTitle("Fire TV update available")
                            .setMessage("Version "+info.versionName+" is ready. Installed: "+version()+".\n\nDownload it here, then approve installation on your TV. Your playlist, favorites, and history stay saved.")
                            .setPositiveButton("Download update",(which,button)->download(info)).setNegativeButton("Later",null).show();
                    } else if(manual)dialog=new AlertDialog.Builder(activity).setTitle("IPTV Fire TV "+version()).setMessage("You have the latest published Fire TV version.").setPositiveButton("OK",null).show();
                });
            } catch(Exception error) {
                handler.post(()->{if(request!=operation)return;busy=false;if(manual&&visible())error("Could not check for updates. Check your connection and try again later.");});
            }
        });
    }
    private void error(String message) {dialog=new AlertDialog.Builder(activity).setTitle("App update").setMessage(message).setPositiveButton("OK",null).show();}
    private void download(UpdateInfo info) {
        if(!visible()||busy)return;
        busy=true;cancelled=false;final int request=++operation;
        LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);layout.setPadding(32,20,32,20);
        TextView label=new TextView(activity);label.setText("Downloading update · 0%");layout.addView(label);
        ProgressBar bar=new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);bar.setMax(100);layout.addView(bar);
        dialog=new AlertDialog.Builder(activity).setTitle("IPTV Fire TV "+info.versionName).setView(layout).setCancelable(false)
            .setNegativeButton("Cancel",(which,button)->{cancelled=true;operation++;if(job!=null)job.cancel(true);busy=false;}).show();
        job=worker.submit(()->{
            try {
                File apk=UpdateService.download(activity,info,percent->handler.post(()->{if(!closed&&!cancelled&&request==operation){bar.setProgress(percent);label.setText("Downloading update · "+percent+"%");}}));
                handler.post(()->{if(closed||cancelled||request!=operation)return;busy=false;downloaded=apk;downloadedInfo=info;if(dialog!=null)dialog.dismiss();if(visible())install();});
            } catch(Exception error) {
                handler.post(()->{if(request!=operation)return;busy=false;if(dialog!=null)dialog.dismiss();if(!cancelled&&visible())error("Could not download or verify the update. Check your connection and free space, then try again.");});
            }
        });
    }
    private void install() {
        if(downloaded==null||downloadedInfo==null||!visible())return;
        try {
            UpdateService.verify(activity,downloaded,downloadedInfo);
            if(Build.VERSION.SDK_INT>=26&&!activity.getPackageManager().canRequestPackageInstalls()) {
                dialog=new AlertDialog.Builder(activity).setTitle("Allow app updates")
                    .setMessage("Allow IPTV Fire TV to install apps, then return here and select Updates again. Installation still needs your approval.")
                    .setPositiveButton("Open settings",(which,button)->{
                        try{activity.startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())));}
                        catch(Exception ignored){error("Open Fire TV Settings → My Fire TV → Developer Options → Install unknown apps, and allow IPTV Fire TV.");}
                    }).setNegativeButton("Later",null).show();return;
            }
            Uri uri=FileProvider.getUriForFile(activity,activity.getPackageName()+".updates",downloaded);
            Intent installer=new Intent(Intent.ACTION_INSTALL_PACKAGE).setDataAndType(uri,"application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            activity.startActivity(installer);
        } catch(Exception ignored) {
            error("Could not open the installer. On Fire OS 6, enable Apps from Unknown Sources under Settings → My Fire TV → Developer Options, then select Updates again.");
        }
    }
    void close() {closed=true;operation++;if(job!=null)job.cancel(true);if(dialog!=null)dialog.dismiss();}
}
