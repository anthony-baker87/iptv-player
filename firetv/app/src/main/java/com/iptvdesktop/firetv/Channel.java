package com.iptvdesktop.firetv;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class Channel {
    public final String name, url, group, id, mimeType;
    public final Map<String, String> headers;
    public Channel(String name, String url, String group, String id, String mimeType, Map<String,String> headers) {
        this.name = name == null || name.trim().isEmpty() ? "Unknown channel" : name.trim();
        this.url = url;
        this.group = group == null || group.isEmpty() ? "Other" : group;
        this.id = id == null ? "" : id;
        this.mimeType = mimeType == null ? "" : mimeType;
        this.headers = headers.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(new LinkedHashMap<>(headers));
    }
    public String key() { return id.isEmpty() ? url : id.length() + ":" + id + name.length() + ":" + name + group; }
    public JSONObject json() throws JSONException {
        JSONObject object = new JSONObject();
        object.put("name",name).put("url",url).put("group",group).put("id",id).put("mime",mimeType);
        JSONObject requestHeaders = new JSONObject();
        for (Map.Entry<String,String> header : headers.entrySet()) requestHeaders.put(header.getKey(),header.getValue());
        return object.put("headers", requestHeaders);
    }
    public static Channel fromJson(JSONObject object) throws JSONException {
        Map<String,String> headers = new LinkedHashMap<>();
        JSONObject values = object.optJSONObject("headers");
        if (values != null) {
            for (String key : new String[]{"User-Agent", "Referer"}) if (values.has(key)) headers.put(key, values.getString(key));
        }
        return new Channel(object.getString("name"),object.getString("url"),object.optString("group"),object.optString("id"),object.optString("mime"),headers);
    }
}
