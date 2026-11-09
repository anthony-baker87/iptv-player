package com.iptvdesktop.firetv;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.net.URI;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaylistParser {
    private static final Pattern ATTRIBUTE = Pattern.compile("([a-zA-Z0-9_-]+)=\"([^\"]*)\"");
    static final int MAX_CHANNELS = 200000;
    private PlaylistParser() {}
    public static boolean isHttp(String url) {
        try { URI uri = URI.create(url); return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())); }
        catch (IllegalArgumentException exception) { return false; }
    }
    public static Channel direct(String url, String mime) {
        if (!isHttp(url)) throw new IllegalArgumentException("Use an HTTP or HTTPS playlist or stream URL.");
        return new Channel("Direct stream",url,"Other","",mime,Collections.emptyMap());
    }
    public static List<Channel> parse(String input, String baseUrl) {
        try { return parse(new StringReader(input), baseUrl); }
        catch (IOException error) { throw new IllegalArgumentException("Could not read the playlist.", error); }
    }
    public static List<Channel> parse(Reader input, String baseUrl) throws IOException { return parse(input,baseUrl,count->{}); }
    static List<Channel> parse(Reader input, String baseUrl, IntConsumer progress) throws IOException {
        BufferedReader reader = new BufferedReader(input, 32768);
        String first;
        do { first = readLine(reader); } while (first != null && first.trim().isEmpty());
        if (first == null || !first.replace("\uFEFF", "").trim().startsWith("#EXTM3U"))
            throw new IllegalArgumentException("The URL did not return an M3U playlist. Check the link and account details.");
        List<Channel> channels = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String,String> attrs = null;
        Map<String,String> headers = new LinkedHashMap<>();
        String name = "";
        String raw;
        while ((raw = readLine(reader)) != null) {
            String line = raw.trim();
            if (line.startsWith("#EXT-X-")) return Collections.singletonList(direct(baseUrl,"application/x-mpegURL"));
            if (line.startsWith("#EXTINF:")) {
                attrs = new LinkedHashMap<>();
                Matcher matcher = ATTRIBUTE.matcher(line);
                while (matcher.find()) attrs.put(matcher.group(1).toLowerCase(Locale.ROOT),matcher.group(2));
                boolean quoted = false; int comma = -1;
                for (int i=0;i<line.length();i++) { if (line.charAt(i)=='\"') quoted=!quoted; if (line.charAt(i)==','&&!quoted) {comma=i;break;} }
                name = comma < 0 ? "" : line.substring(comma+1).trim();
                if (name.isEmpty()) name = attrs.containsKey("tvg-name") ? attrs.get("tvg-name") : "Unknown channel";
                headers = new LinkedHashMap<>();
            } else if (attrs != null && line.startsWith("#EXTGRP:")) attrs.put("group-title",line.substring(8));
            else if (attrs != null && line.startsWith("#EXTVLCOPT:http-user-agent=")) putHeader(headers,"User-Agent",line.substring(line.indexOf('=')+1));
            else if (attrs != null && line.startsWith("#EXTVLCOPT:http-referrer=")) putHeader(headers,"Referer",line.substring(line.indexOf('=')+1));
            else if (!line.isEmpty() && !line.startsWith("#") && attrs != null) {
                String[] parts = line.split("\\|",2);
                if (parts.length>1) for (String pair : parts[1].split("&")) {
                    String[] kv=pair.split("=",2);
                    if (kv.length==2) {
                        try { String key=URLDecoder.decode(kv[0],"UTF-8"); String value=URLDecoder.decode(kv[1],"UTF-8");
                            if (key.equalsIgnoreCase("User-Agent")) putHeader(headers,"User-Agent",value);
                            if (key.equalsIgnoreCase("Referer")) putHeader(headers,"Referer",value);
                        } catch (Exception ignored) { /* Unsupported header options do not invalidate a channel. */ }
                    }
                }
                try {
                    String url = URI.create(baseUrl).resolve(parts[0].trim()).toString();
                    if (isHttp(url) && seen.add(url)) channels.add(new Channel(name,url,attrs.get("group-title"),attrs.get("tvg-id"),"",headers));
                } catch (IllegalArgumentException ignored) { /* Skip malformed stream URLs. */ }
                if(channels.size()%250==0)progress.accept(channels.size());
                attrs=null;
                if (channels.size()>MAX_CHANNELS) throw new IllegalArgumentException("Playlist exceeds 200,000 channels. Choose a provider category or smaller playlist for this device.");
            }
        }
        if (channels.isEmpty()) throw new IllegalArgumentException("No supported HTTP streams were found in this playlist.");
        progress.accept(channels.size());
        return channels;
    }
    private static String readLine(BufferedReader reader) throws IOException {
        StringBuilder line = new StringBuilder();
        int value;
        while ((value = reader.read()) != -1) {
            if (value == '\n') return line.toString();
            if (line.length() >= 65536) throw new IllegalArgumentException("A playlist entry is too long (over 64 KB).");
            line.append((char)value);
        }
        return line.length() == 0 ? null : line.toString();
    }
    private static void putHeader(Map<String,String> headers, String key, String value) {
        if (!value.contains("\r") && !value.contains("\n") && value.length()<2048) headers.put(key,value);
    }
}
