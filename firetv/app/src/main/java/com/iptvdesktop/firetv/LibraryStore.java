package com.iptvdesktop.firetv;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import org.json.JSONObject;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class LibraryStore {
    final SharedPreferences preferences;
    private final AtomicFile playlistFile;
    private final Set<String> favorites;
    private final Map<String,PlaybackHistory> history=new HashMap<>();
    LibraryStore(Context context) {
        preferences=context.getSharedPreferences("library",Context.MODE_PRIVATE);
        playlistFile=new AtomicFile(new File(context.getFilesDir(),"playlist.json"));
        favorites=new HashSet<>(preferences.getStringSet("favorites",Collections.emptySet()));
        try {
            JSONObject data=new JSONObject(preferences.getString("history","{}"));
            Iterator<String> keys=data.keys();while(keys.hasNext()) {String key=keys.next();try{history.put(key,PlaybackHistory.fromJson(data.getJSONObject(key)));}catch(Exception ignored){}}
        } catch(Exception ignored) { /* A damaged history does not prevent playback. */ }
    }
    synchronized boolean favorite(Channel channel) {return favorites.contains(channel.key());}
    synchronized void toggleFavorite(Channel channel) {if(!favorites.add(channel.key()))favorites.remove(channel.key());preferences.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();}
    synchronized Map<String,PlaybackHistory> history() {return new HashMap<>(history);}
    synchronized PlaybackHistory history(Channel channel) {return history.get(channel.key());}
    synchronized void progress(Channel channel,long position,long duration,boolean ended,boolean reset) {
        if(channel==null||duration<=0||position<0||position<1000&&!reset)return;
        boolean complete=ended||position>duration/2&&duration-position<=Math.min(30000,duration/50);
        history.put(channel.key(),new PlaybackHistory(channel,position,duration,complete,System.currentTimeMillis()));
        List<PlaybackHistory> sorted=new ArrayList<>(history.values());
        Collections.sort(sorted,Comparator.comparingLong((PlaybackHistory item)->item.updated).reversed());
        while(sorted.size()>200) {PlaybackHistory old=sorted.remove(sorted.size()-1);history.remove(old.channel.key());}
        try {JSONObject data=new JSONObject();for(Map.Entry<String,PlaybackHistory> item:history.entrySet())data.put(item.getKey(),item.getValue().json());preferences.edit().putString("history",data.toString()).apply();}
        catch(Exception ignored) { /* Next progress event retries persistence. */ }
    }
    List<Channel> readPlaylist() throws Exception {return readPlaylist(new PlaylistProgress());}
    List<Channel> readPlaylist(PlaylistProgress progress) throws Exception {
        progress.opening(0);
        try(FileInputStream input=playlistFile.openRead();JsonReader reader=new JsonReader(new InputStreamReader(input,StandardCharsets.UTF_8))) {
            List<Channel> channels=new ArrayList<>();
            reader.beginArray();
            while(reader.hasNext()) {
                if(channels.size()>=PlaylistParser.MAX_CHANNELS)throw new IllegalArgumentException("Cached playlist exceeds 200,000 channels.");
                String name="",url="",group="",id="",mime="";
                Map<String,String> headers=new HashMap<>();
                reader.beginObject();
                while(reader.hasNext()) {
                    switch(reader.nextName()) {
                        case "name": name=reader.nextString();break;
                        case "url": url=reader.nextString();break;
                        case "group": group=reader.nextString();break;
                        case "id": id=reader.nextString();break;
                        case "mime": mime=reader.nextString();break;
                        case "headers":
                            reader.beginObject();
                            while(reader.hasNext()) {String key=reader.nextName();String value=reader.nextString();if(key.equals("User-Agent")||key.equals("Referer"))headers.put(key,value);}
                            reader.endObject();break;
                        default: reader.skipValue();
                    }
                }
                reader.endObject();
                channels.add(new Channel(name,url,group,id,mime,headers));
                if(channels.size()%250==0)progress.opening(channels.size());
            }
            reader.endArray();progress.opening(channels.size());return channels;
        }
    }
    void writePlaylist(List<Channel> channels) throws Exception {writePlaylist(channels,new PlaylistProgress());}
    void writePlaylist(List<Channel> channels,PlaylistProgress progress) throws Exception {
        progress.saving(0,channels.size());
        FileOutputStream output=playlistFile.startWrite();
        try {
            JsonWriter writer=new JsonWriter(new BufferedWriter(new OutputStreamWriter(output,StandardCharsets.UTF_8),65536));
            writer.beginArray();
            int saved=0;
            for(Channel channel:channels) {
                writer.beginObject().name("name").value(channel.name).name("url").value(channel.url)
                    .name("group").value(channel.group).name("id").value(channel.id).name("mime").value(channel.mimeType).name("headers").beginObject();
                for(Map.Entry<String,String> header:channel.headers.entrySet())writer.name(header.getKey()).value(header.getValue());
                writer.endObject().endObject();
                if(++saved%250==0)progress.saving(saved,channels.size());
            }
            writer.endArray();writer.flush();playlistFile.finishWrite(output);progress.saving(channels.size(),channels.size());
        } catch(Exception error) {playlistFile.failWrite(output);throw error;}
    }
}
