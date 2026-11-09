package com.iptvdesktop.firetv;
import org.junit.Test;
import org.json.JSONObject;
import static org.junit.Assert.*;
public class UpdateInfoTest {
    private JSONObject manifest() throws Exception {
        return new JSONObject().put("packageName","com.iptvdesktop.firetv").put("versionCode",1001).put("versionName","1.1.1")
            .put("apkUrl","https://github.com/anthony-baker87/iptv-player/releases/download/firetv-v1.1.1/IPTV-FireTV-1.1.1.apk")
            .put("sha256",new String(new char[64]).replace('\0','a')).put("size",6000000);
    }
    @Test public void acceptsOnlyTheExpectedReleaseAndPackage() throws Exception {
        UpdateInfo info=new UpdateInfo(manifest());assertEquals(1001,info.versionCode);assertEquals(6000000,info.size);
        assertTrue(UpdateInfo.releaseAsset("https://github.com/anthony-baker87/iptv-player/releases/download/firetv-v1.1.1/firetv-update.json","/firetv-update.json"));
    }
    @Test public void rejectsWrongHostPackageDigestAndOversizedDownloads() throws Exception {
        for(JSONObject data:new JSONObject[]{manifest().put("packageName","another.app"),manifest().put("sha256","invalid"),
            manifest().put("size",UpdateInfo.MAX_APK_BYTES+1),manifest().put("apkUrl","http://example.test/update.apk")}) {
            try {new UpdateInfo(data);fail("Should reject invalid update");}catch(IllegalArgumentException expected){}
        }
        assertFalse(UpdateInfo.releaseAsset("https://github.com/another/repo/releases/download/firetv-v1/a.apk",".apk"));
    }
}
