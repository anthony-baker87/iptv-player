package com.iptvdesktop.firetv;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.util.*;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=25)
public class LibraryStoreTest {
    @Test public void librarySurvivesRecreationAndStartOverResetsProgress() throws Exception {
        Context context=RuntimeEnvironment.getApplication();LibraryStore store=new LibraryStore(context);Channel channel=new Channel("Show S01E02","https://example.test/show.mp4","Series","id","",Collections.singletonMap("Referer","https://example.test"));
        store.toggleFavorite(channel);store.progress(channel,15000,60000,false,false);store.writePlaylist(Collections.singletonList(channel));store.preferences.edit().putFloat("volume",0.35f).putBoolean("muted",true).commit();
        LibraryStore recreated=new LibraryStore(context);assertTrue(recreated.favorite(channel));assertEquals(15000,recreated.history(channel).position);assertEquals("https://example.test",recreated.readPlaylist().get(0).headers.get("Referer"));assertEquals(0.35f,recreated.preferences.getFloat("volume",1),0.001);assertTrue(recreated.preferences.getBoolean("muted",false));
        recreated.progress(channel,0,60000,false,true);assertFalse(new LibraryStore(context).history(channel).canResume());
    }
    @Test public void largeCacheRoundTripsWithoutTheOldThirtyMegabyteLimit() throws Exception {
        LibraryStore store=new LibraryStore(RuntimeEnvironment.getApplication());
        String suffix=new String(new char[700]).replace('\0','x');
        List<Channel> channels=new ArrayList<>();
        for(int i=0;i<42000;i++)channels.add(new Channel("Episode "+i,"https://example.test/"+i+"?"+suffix,"Series","id"+i,"",Collections.emptyMap()));
        PlaylistProgress progress=new PlaylistProgress();
        store.writePlaylist(channels,progress);assertEquals(100,progress.percent());
        List<Channel> loaded=new LibraryStore(RuntimeEnvironment.getApplication()).readPlaylist();
        assertEquals(channels.size(),loaded.size());assertEquals(channels.get(41999).url,loaded.get(41999).url);
    }
}
