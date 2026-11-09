package com.iptvdesktop.firetv;

import org.json.JSONObject;
import java.net.URI;
import java.util.Locale;

final class UpdateInfo {
    static final String REPOSITORY="anthony-baker87/iptv-player";
    static final long MAX_APK_BYTES=64L*1024*1024;
    final int versionCode;
    final String versionName,url,sha256;
    final long size;
    UpdateInfo(JSONObject data) throws Exception {
        versionCode=data.getInt("versionCode");versionName=data.getString("versionName");
        url=data.getString("apkUrl");sha256=data.getString("sha256").toLowerCase(Locale.ROOT);size=data.getLong("size");
        if(!data.getString("packageName").equals("com.iptvdesktop.firetv")||versionCode<=0||versionName.length()>80
            ||!sha256.matches("[0-9a-f]{64}")||size<=0||size>MAX_APK_BYTES||!releaseAsset(url,".apk"))
            throw new IllegalArgumentException("Invalid Fire TV update information.");
    }
    static boolean releaseAsset(String value,String suffix) {
        try {
            URI uri=URI.create(value);String path=uri.getPath();
            return "https".equals(uri.getScheme())&&"github.com".equals(uri.getHost())&&uri.getUserInfo()==null
                &&uri.getPort()==-1&&uri.getQuery()==null&&uri.getFragment()==null
                &&path.startsWith("/"+REPOSITORY+"/releases/download/firetv-v")&&path.endsWith(suffix)&&!path.contains("..");
        } catch(Exception ignored) {return false;}
    }
}
