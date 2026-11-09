package com.iptvdesktop.firetv;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlaybackHistory {
    private static final Pattern EPISODE = Pattern.compile("\\b(?:S(\\d{1,3})\\s*E(\\d{1,3})|(\\d{1,3})x(\\d{1,3}))\\b",Pattern.CASE_INSENSITIVE);
    public final Channel channel;
    public final long position, duration, updated;
    public final boolean completed;
    public PlaybackHistory(Channel channel,long position,long duration,boolean completed,long updated) {
        this.channel=channel;this.position=position;this.duration=duration;this.completed=completed;this.updated=updated;
    }
    public static boolean canResume(long position,long duration,boolean completed) { return !completed && duration>0 && position>=5000 && position<duration-Math.min(30000,duration/50); }
    public boolean canResume() { return canResume(position,duration,completed); }
    public static String series(String name) {
        Matcher matcher=EPISODE.matcher(name);
        return matcher.find() ? name.substring(0,matcher.start()).replaceAll("[\\s._-]+$", "").trim() : "";
    }
    public String description() { return (series(channel.name).isEmpty()?"":"Last watched · ") + (completed?"Finished":time(position)+" / "+time(duration)); }
    public static String time(long milliseconds) { long seconds=Math.max(0,milliseconds/1000);return seconds>=3600?String.format(Locale.ROOT,"%d:%02d:%02d",seconds/3600,seconds/60%60,seconds%60):String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60); }
    public JSONObject json() throws JSONException { return new JSONObject().put("channel",channel.json()).put("position",position).put("duration",duration).put("completed",completed).put("updated",updated); }
    public static PlaybackHistory fromJson(JSONObject json) throws JSONException { return new PlaybackHistory(Channel.fromJson(json.getJSONObject("channel")),json.getLong("position"),json.getLong("duration"),json.optBoolean("completed"),json.getLong("updated")); }
    public static List<Channel> visible(Map<String,PlaybackHistory> history,Map<String,Channel> available) {
        List<PlaybackHistory> sorted=new ArrayList<>(history.values());
        Collections.sort(sorted,Comparator.comparingLong((PlaybackHistory item)->item.updated).reversed());
        List<Channel> result=new ArrayList<>();Set<String> seen=new HashSet<>();
        for (PlaybackHistory entry:sorted) {
            Channel channel=available.get(entry.channel.key());if(channel==null)continue;
            String series=series(channel.name).toLowerCase(Locale.ROOT);
            String key=channel.group+"\n"+series;
            if(!series.isEmpty()&&!seen.add(key))continue;
            result.add(channel);
        }
        return result;
    }
}
