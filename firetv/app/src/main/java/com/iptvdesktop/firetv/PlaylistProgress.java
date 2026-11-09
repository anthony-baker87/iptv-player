package com.iptvdesktop.firetv;

import java.util.Locale;

/** Thread-safe progress shared by the playlist worker and the TV's UI timer. */
final class PlaylistProgress {
    private String stage="Connecting";
    private long bytes,total=-1;
    private int channels,saved,saveTotal;
    synchronized void downloading(long count,long size) {stage="Downloading";bytes=count;total=size;}
    synchronized void parsed(int count) {channels=count;}
    synchronized void saving(int count,int size) {stage="Saving list";saved=count;saveTotal=size;}
    synchronized void opening(int count) {stage="Opening saved list";channels=count;}
    synchronized int percent() {
        if(stage.equals("Saving list")&&saveTotal>0)return (int)(100L*saved/saveTotal);
        if(stage.equals("Downloading")&&total>0)return (int)Math.min(99,100L*bytes/total);
        return -1;
    }
    synchronized String text(long elapsedMillis) {
        long seconds=Math.max(0,elapsedMillis/1000);
        String elapsed=String.format(Locale.US,"%d:%02d elapsed",seconds/60,seconds%60);
        int percent=percent();String detail;
        if(stage.equals("Downloading")) detail=String.format(Locale.US,"%.1f MB%s · %,d channels",bytes/1048576.0,total>0?String.format(Locale.US," / %.1f MB",total/1048576.0):"",channels);
        else if(stage.equals("Saving list"))detail=String.format(Locale.US,"%,d / %,d channels",saved,saveTotal);
        else if(stage.equals("Opening saved list"))detail=String.format(Locale.US,"%,d channels",channels);
        else detail="Waiting for provider";
        return stage+(percent>=0?" · "+percent+"%":"")+" · "+elapsed+"\n"+detail;
    }
}
