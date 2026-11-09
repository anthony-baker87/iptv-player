package com.iptvdesktop.firetv;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.LongConsumer;

final class PlaylistLoader {
    static final long MAX_BYTES = 256L * 1024 * 1024;
    static InputStream limited(InputStream input) { return limited(input,count->{}); }
    private static InputStream limited(InputStream input,LongConsumer progress) {
        return new FilterInputStream(input) {
            long count;
            private void checked(int bytes) {
                if(bytes > 0 && (count += bytes) > MAX_BYTES)
                    throw new IllegalArgumentException("Playlist exceeds 256 MB. Choose a provider category or smaller playlist for this device.");
                if(bytes>0)progress.accept(count);
            }
            @Override public int read() throws IOException {int value=in.read(); checked(value<0?0:1); return value;}
            @Override public int read(byte[] buffer,int offset,int length) throws IOException {int bytes=in.read(buffer,offset,length);checked(bytes);return bytes;}
        };
    }
    static List<Channel> load(String source) throws Exception {return load(source,new PlaylistProgress());}
    static List<Channel> load(String source,PlaylistProgress progress) throws Exception {
        if(!PlaylistParser.isHttp(source))throw new IllegalArgumentException("Enter a valid HTTP or HTTPS URL.");
        String path=URI.create(source).getPath().toLowerCase(Locale.ROOT);
        if(path.matches(".*\\.(mp4|m4v|mkv|webm|ts|mpd)$"))return Collections.singletonList(PlaylistParser.direct(source,""));
        URL current=new URL(source);
        for(int redirect=0;redirect<6;redirect++) {
            HttpURLConnection connection=(HttpURLConnection)current.openConnection();
            connection.setConnectTimeout(15000);connection.setReadTimeout(20000);connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent","Mozilla/5.0");
            try {
                int status=connection.getResponseCode();
                if(status>=300&&status<400) {
                    String location=connection.getHeaderField("Location");if(location==null)throw new IllegalArgumentException("Playlist redirect has no destination.");
                    current=new URL(current,location);if(!PlaylistParser.isHttp(current.toString()))throw new IllegalArgumentException("Unsupported playlist redirect.");continue;
                }
                if(status<200||status>=300)throw new IllegalArgumentException("Playlist request failed (HTTP "+status+"). Check the link or provider account.");
                String type=connection.getContentType();type=type==null?"":type.toLowerCase(Locale.ROOT);
                if(type.startsWith("video/")||type.startsWith("audio/")&&!type.contains("mpegurl"))return Collections.singletonList(PlaylistParser.direct(current.toString(),""));
                if(type.contains("dash+xml"))return Collections.singletonList(PlaylistParser.direct(current.toString(),"application/dash+xml"));
                final long total=connection.getContentLengthLong();
                progress.downloading(0,total);
                try(InputStream input=limited(connection.getInputStream(),count->progress.downloading(count,total))) {
                    return PlaylistParser.parse(new InputStreamReader(input,StandardCharsets.UTF_8),current.toString(),progress::parsed);
                }
            } finally {connection.disconnect();}
        }
        throw new IllegalArgumentException("Playlist redirected too many times.");
    }
}
