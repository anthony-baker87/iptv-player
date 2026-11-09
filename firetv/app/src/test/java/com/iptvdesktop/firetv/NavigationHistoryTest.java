package com.iptvdesktop.firetv;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;
public class NavigationHistoryTest {
    private Channel channel(String name,String url){return new Channel(name,url,"Shows","","",Collections.emptyMap());}
    @Test public void lastTogglesWithoutPlaybackEventsAndKeepsDistinctSources() {
        Channel a=channel("A","https://example.test/a.ts"),b=channel("B","https://example.test/b.ts"),failed=channel("Failed","https://example.test/missing");LastChannels last=new LastChannels();
        assertNull(last.target(null));last.opened(a);assertNull(last.target(a));last.opened(b);assertEquals(a,last.target(b));last.opened(b);assertEquals(a,last.target(b));last.opened(a);assertEquals(b,last.target(a));assertEquals(a,last.target(failed));
    }
    @Test public void resumeExcludesFinishedAndVeryShortPositions(){assertFalse(PlaybackHistory.canResume(1000,600000,false));assertTrue(PlaybackHistory.canResume(80000,600000,false));assertFalse(PlaybackHistory.canResume(599000,600000,false));assertFalse(PlaybackHistory.canResume(80000,600000,true));}
    @Test public void historyShowsLatestEpisodeForEachSeries(){Channel one=channel("Example Show S01E01","one"),two=channel("Example Show S01E02","two"),other=channel("Different 1x04","other");Map<String,Channel> available=new HashMap<>();Map<String,PlaybackHistory> history=new HashMap<>();int now=0;for(Channel channel:Arrays.asList(one,two,other)){available.put(channel.key(),channel);history.put(channel.key(),new PlaybackHistory(channel,10000,100000,false,now++));}List<Channel> visible=PlaybackHistory.visible(history,available);assertEquals(2,visible.size());assertEquals(other,visible.get(0));assertEquals(two,visible.get(1));assertEquals("Example Show",PlaybackHistory.series(one.name));}
}
