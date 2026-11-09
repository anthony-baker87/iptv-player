package com.iptvdesktop.firetv;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.function.IntConsumer;

final class UpdateService {
    private UpdateService() {}
    static HttpURLConnection open(String address) throws Exception {
        URL url=new URL(address);
        for(int i=0;i<6;i++) {
            String host=url.getHost();
            if(!url.getProtocol().equals("https")||!(host.equals("github.com")||host.equals("api.github.com")||host.endsWith(".githubusercontent.com")))
                throw new IllegalArgumentException("Unsupported update download location.");
            HttpURLConnection connection=(HttpURLConnection)url.openConnection();
            connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","IPTV-FireTV-Updater");
            connection.setRequestProperty("Accept","application/vnd.github+json");
            int status=connection.getResponseCode();
            if(status>=300&&status<400) {
                String location=connection.getHeaderField("Location");connection.disconnect();
                if(location==null)throw new IllegalArgumentException("Update redirect was incomplete.");
                url=new URL(url,location);continue;
            }
            if(status!=200){connection.disconnect();throw new IllegalArgumentException("Update server returned HTTP "+status+". Try again later.");}
            return connection;
        }
        throw new IllegalArgumentException("Too many update redirects.");
    }
    static String json(String url) throws Exception {
        HttpURLConnection connection=open(url);
        try(InputStream input=connection.getInputStream();ByteArrayOutputStream data=new ByteArrayOutputStream()) {
            byte[] buffer=new byte[8192];int count;
            while((count=input.read(buffer))!=-1) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                data.write(buffer,0,count);if(data.size()>1024*1024)throw new IllegalArgumentException("Update response was too large.");
            }
            return new String(data.toByteArray(),StandardCharsets.UTF_8);
        } finally {connection.disconnect();}
    }
    static UpdateInfo check() throws Exception {
        JSONArray releases=new JSONArray(json("https://api.github.com/repos/"+UpdateInfo.REPOSITORY+"/releases?per_page=30"));
        for(int i=0;i<releases.length();i++) {
            JSONObject release=releases.getJSONObject(i);
            if(release.optBoolean("draft")||release.optBoolean("prerelease")||!release.optString("tag_name").startsWith("firetv-v"))continue;
            JSONArray assets=release.getJSONArray("assets");
            for(int j=0;j<assets.length();j++) {
                JSONObject asset=assets.getJSONObject(j);
                if(!asset.optString("name").equals("firetv-update.json"))continue;
                String url=asset.getString("browser_download_url");
                if(!UpdateInfo.releaseAsset(url,"/firetv-update.json"))throw new IllegalArgumentException("Invalid update manifest location.");
                return new UpdateInfo(new JSONObject(json(url)));
            }
        }
        return null;
    }
    static boolean sameSigners(Signature[] current,Signature[] downloaded) {
        if(current==null||downloaded==null||current.length==0||current.length!=downloaded.length)return false;
        for(Signature signer:current)if(!Arrays.asList(downloaded).contains(signer))return false;
        return true;
    }
    static void verify(Context context,File apk,UpdateInfo info) throws Exception {
        PackageManager manager=context.getPackageManager();
        PackageInfo installed=manager.getPackageInfo(context.getPackageName(),PackageManager.GET_SIGNATURES);
        PackageInfo candidate=manager.getPackageArchiveInfo(apk.getAbsolutePath(),PackageManager.GET_SIGNATURES);
        if(candidate==null||!context.getPackageName().equals(candidate.packageName)||candidate.versionCode!=info.versionCode
            ||candidate.versionCode<=installed.versionCode||!sameSigners(installed.signatures,candidate.signatures))
            throw new IllegalArgumentException("This update does not match the installed app's identity or signing key.");
    }
    static File download(Context context,UpdateInfo info,IntConsumer progress) throws Exception {
        File directory=new File(context.getFilesDir(),"updates");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IllegalArgumentException("Could not create the update folder.");
        File part=File.createTempFile("download-",".apk",directory),apk=new File(directory,"update.apk");
        HttpURLConnection connection=open(info.url);boolean success=false;
        try(InputStream input=connection.getInputStream();FileOutputStream output=new FileOutputStream(part)) {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[65536];long bytes=0;int count,last=-1;
            while((count=input.read(buffer))!=-1) {
                if(Thread.currentThread().isInterrupted())throw new InterruptedException();
                bytes+=count;if(bytes>info.size||bytes>UpdateInfo.MAX_APK_BYTES)throw new IllegalArgumentException("Update size did not match the release.");
                output.write(buffer,0,count);digest.update(buffer,0,count);
                int percent=(int)(100*bytes/info.size);if(percent!=last){last=percent;progress.accept(percent);}
            }
            StringBuilder hash=new StringBuilder();for(byte value:digest.digest())hash.append(String.format(java.util.Locale.US,"%02x",value&255));
            if(bytes!=info.size||!hash.toString().equals(info.sha256))throw new IllegalArgumentException("Update integrity check failed. Please download again.");
            output.getFD().sync();verify(context,part,info);
            if(Thread.currentThread().isInterrupted())throw new InterruptedException();
            if(apk.exists()&&!apk.delete())throw new IllegalArgumentException("Could not replace the previous update download.");
            if(!part.renameTo(apk))throw new IllegalArgumentException("Could not save the update download.");
            success=true;return apk;
        } finally {connection.disconnect();if(!success)part.delete();}
    }
}
