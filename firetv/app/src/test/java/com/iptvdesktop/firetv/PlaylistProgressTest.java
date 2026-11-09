package com.iptvdesktop.firetv;
import org.junit.Test;
import static org.junit.Assert.*;
public class PlaylistProgressTest {
    @Test public void knownDownloadSizeAndElapsedTime() {
        PlaylistProgress progress=new PlaylistProgress();progress.downloading(5*1048576,10*1048576);progress.parsed(12000);
        assertEquals(50,progress.percent());assertTrue(progress.text(65000).contains("1:05 elapsed"));
        assertTrue(progress.text(65000).contains("5.0 MB / 10.0 MB"));assertTrue(progress.text(65000).contains("12,000 channels"));
        progress.downloading(10*1048576,10*1048576);assertEquals(99,progress.percent());
    }
    @Test public void unknownSizeDoesNotInventAPercentage() {
        PlaylistProgress progress=new PlaylistProgress();progress.downloading(1048576,-1);progress.parsed(2000);
        assertEquals(-1,progress.percent());assertFalse(progress.text(9000).contains("%"));assertTrue(progress.text(9000).contains("1.0 MB"));
    }
    @Test public void savingAndCachedLoadingAreSeparateStages() {
        PlaylistProgress progress=new PlaylistProgress();progress.saving(250,1000);
        assertEquals(25,progress.percent());assertTrue(progress.text(20000).contains("Saving list"));
        progress.saving(1000,1000);assertEquals(100,progress.percent());
        progress.opening(500);assertEquals(-1,progress.percent());assertTrue(progress.text(1000).contains("Opening saved list"));
    }
}
