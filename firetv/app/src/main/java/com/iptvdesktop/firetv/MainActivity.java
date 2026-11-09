package com.iptvdesktop.firetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.TrackSelectionParameters;
import androidx.media3.common.Tracks;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy;
import androidx.media3.session.MediaSession;
import androidx.media3.ui.PlayerView;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

@UnstableApi
public final class MainActivity extends Activity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final ExecutorService io=Executors.newFixedThreadPool(2);
    private final AtomicInteger filterGeneration=new AtomicInteger();
    private final LastChannels lastChannels=new LastChannels();
    private final Set<String> failed=new HashSet<>();
    private List<Channel> channels=Collections.emptyList();
    private LibraryStore store;
    private AppUpdater updater;
    private ChannelAdapter adapter;
    private RecyclerView channelList;
    private EditText search;
    private TextView status,count,empty,title;
    private View sidebar,toolbar,hint;
    private View playlistProgressView;
    private TextView playlistProgressText;
    private ProgressBar playlistProgressBar;
    private PlaylistProgress playlistProgress;
    private long playlistStarted;
    private final Runnable playlistTimer=new Runnable() { @Override public void run() {
        if(destroyed||playlistProgress==null)return;
        int percent=playlistProgress.percent();
        playlistProgressBar.setIndeterminate(percent<0);
        if(percent>=0)playlistProgressBar.setProgress(percent);
        playlistProgressText.setText(playlistProgress.text(SystemClock.elapsedRealtime()-playlistStarted));
        handler.postDelayed(this,500);
    }};
    private void beginPlaylistProgress(PlaylistProgress progress) {
        playlistProgress=progress;playlistStarted=SystemClock.elapsedRealtime();
        playlistProgressView.setVisibility(View.VISIBLE);handler.removeCallbacks(playlistTimer);playlistTimer.run();
    }
    private void endPlaylistProgress() {
        playlistProgress=null;handler.removeCallbacks(playlistTimer);playlistProgressView.setVisibility(View.GONE);
    }
    private PlayerView playerView;
    private ExoPlayer player;
    private MediaSession mediaSession;
    private Button lastButton,favoriteButton,refreshButton;
    private CheckBox hideFailed;
    private Channel current;
    private String tab="all",captionMode="auto";
    private boolean fullscreen,loading,started,destroyed,pendingResume,restorePlaying=true;
    private long restorePosition,lastSaved,bufferingSince;
    private int streamGeneration,loadGeneration,retries;
    private final Runnable filterAction=()->filter(false);
    private final Runnable ticker=new Runnable() { @Override public void run() {
        if(player!=null&&started) {
            if(player.getPlayWhenReady()&&player.getPlaybackState()==Player.STATE_BUFFERING) {
                if(bufferingSince==0) bufferingSince=SystemClock.elapsedRealtime();
                if(SystemClock.elapsedRealtime()-bufferingSince>20000) { bufferingSince=SystemClock.elapsedRealtime(); retry("The stream stopped responding."); }
            } else bufferingSince=0;
            if(SystemClock.elapsedRealtime()-lastSaved>=5000) { saveProgress(false); lastSaved=SystemClock.elapsedRealtime(); }
            handler.postDelayed(this,1000);
        }
    }};
    @Override public void onCreate(@Nullable Bundle state) {
        super.onCreate(state); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        setContentView(R.layout.activity_main); store=new LibraryStore(this);updater=new AppUpdater(this,io,handler);
        sidebar=findViewById(R.id.sidebar); toolbar=findViewById(R.id.watch_toolbar); hint=findViewById(R.id.remote_hint);
        status=findViewById(R.id.status); count=findViewById(R.id.list_count); empty=findViewById(R.id.empty); title=findViewById(R.id.now_playing);
        playlistProgressView=findViewById(R.id.playlist_progress);playlistProgressText=findViewById(R.id.playlist_progress_text);playlistProgressBar=findViewById(R.id.playlist_progress_bar);
        lastButton=findViewById(R.id.last); favoriteButton=findViewById(R.id.favorite); refreshButton=findViewById(R.id.refresh); search=findViewById(R.id.search); hideFailed=findViewById(R.id.hide_failed);
        playerView=findViewById(R.id.player); playerView.setControllerAutoShow(true); playerView.setControllerHideOnTouch(false); playerView.setControllerShowTimeoutMs(5000); playerView.setShowSubtitleButton(true); playerView.setShowNextButton(false); playerView.setShowPreviousButton(false);
        channelList=findViewById(R.id.channels); channelList.setLayoutManager(new LinearLayoutManager(this)); channelList.setItemAnimator(null);
        adapter=new ChannelAdapter(store,new ChannelAdapter.Actions() { public void play(Channel channel) { choose(channel,false); } public void favorite(Channel channel) { toggleFavorite(channel); } }); channelList.setAdapter(adapter);
        for(int id:new int[]{R.id.settings,R.id.refresh,R.id.updates,R.id.tab_all,R.id.tab_favorites,R.id.tab_history,R.id.last,R.id.favorite,R.id.subtitles,R.id.fullscreen,R.id.volume}) {
            Button button=findViewById(id); button.setBackgroundResource(R.drawable.focus_item); button.setTextColor(Color.WHITE); button.setTextSize(id==R.id.tab_favorites?12:14); button.setAllCaps(false);
        }
        findViewById(R.id.settings).setOnClickListener(view->playlistDialog());
        findViewById(R.id.updates).setOnClickListener(view->updater.check(true));
        refreshButton.setOnClickListener(view->{ String url=store.preferences.getString("playlistUrl",""); if(url.isEmpty()) playlistDialog(); else loadPlaylist(url,true); });
        findViewById(R.id.tab_all).setOnClickListener(view->setTab("all")); findViewById(R.id.tab_favorites).setOnClickListener(view->setTab("favorites")); findViewById(R.id.tab_history).setOnClickListener(view->setTab("history"));
        lastButton.setOnClickListener(view->playLast()); favoriteButton.setOnClickListener(view->{ if(current!=null) toggleFavorite(current); });
        findViewById(R.id.subtitles).setOnClickListener(view->subtitleDialog()); findViewById(R.id.fullscreen).setOnClickListener(view->setFullscreen(true)); findViewById(R.id.volume).setOnClickListener(view->volumeDialog());
        hideFailed.setOnCheckedChangeListener((button,checked)->filter(false));
        search.addTextChangedListener(new TextWatcher() { public void beforeTextChanged(CharSequence text,int start,int count,int after) {} public void onTextChanged(CharSequence text,int start,int before,int count) { handler.removeCallbacks(filterAction); handler.postDelayed(filterAction,200); } public void afterTextChanged(Editable editable) {} });
        search.setOnEditorActionListener((view,action,event)->{ ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0); focusChannels(); return true; });
        updateButtons(); status.setText("Add your playlist to begin. No channels or account details are bundled."); empty.setText("Open Playlist to enter your M3U link."); findViewById(R.id.settings).requestFocus();
        final int cacheGeneration=++loadGeneration;
        final PlaylistProgress cacheProgress=new PlaylistProgress();cacheProgress.opening(0);
        loading=true;updateButtons();beginPlaylistProgress(cacheProgress);
        io.execute(()->{
            List<Channel> saved; try { saved=store.readPlaylist(cacheProgress); } catch(Exception ignored) { saved=Collections.emptyList(); }
            final List<Channel> cached=saved;
            handler.post(()->{ if(destroyed||cacheGeneration!=loadGeneration)return; loading=false;endPlaylistProgress();updateButtons();if(!cached.isEmpty()) { channels=cached; filter(false); String key=store.preferences.getString("lastChannel",""); Channel first=channels.get(0); for(Channel channel:channels)if(channel.key().equals(key)){first=channel;break;} choose(first,false); }
                else { String url=store.preferences.getString("playlistUrl",""); if(!url.isEmpty()) loadPlaylist(url,false); } });
        });
    }
    @Override public void onStart() { super.onStart(); started=true;updater.active(true); createPlayer(); if(current!=null&&!pendingResume) prepare(current,restorePosition,restorePlaying); handler.post(ticker); }
    @Override public void onStop() {
        saveProgress(false); started=false;updater.active(false); handler.removeCallbacks(ticker);
        if(player!=null) { restorePosition=player.isCurrentMediaItemLive()?0:Math.max(0,player.getCurrentPosition()); restorePlaying=player.getPlayWhenReady(); if(mediaSession!=null){mediaSession.release();mediaSession=null;} playerView.setPlayer(null); player.release(); player=null; }
        super.onStop();
    }
    @Override public void onDestroy() { destroyed=true;updater.close(); handler.removeCallbacksAndMessages(null); io.shutdownNow(); super.onDestroy(); }
    private void createPlayer() {
        if(player!=null)return;
        DefaultLoadControl buffering=new DefaultLoadControl.Builder().setBufferDurationsMs(15000,45000,1500,3000).setPrioritizeTimeOverSizeThresholds(false).build();
        player=new ExoPlayer.Builder(this).setLoadControl(buffering).setSeekBackIncrementMs(10000).setSeekForwardIncrementMs(10000).build();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),true); player.setHandleAudioBecomingNoisy(true); player.setWakeMode(C.WAKE_MODE_NETWORK); applyVolume();
        playerView.setPlayer(player); mediaSession=new MediaSession.Builder(this,player).build();
        player.addListener(new Player.Listener() {
            @Override public void onIsPlayingChanged(boolean playing) { if(playing&&current!=null&&matchesCurrent()) { failed.remove(current.key()); setStatus(""); updateButtons(); } }
            @Override public void onPlaybackStateChanged(int state) { if(state==Player.STATE_BUFFERING)setStatus("Buffering…"); if(state==Player.STATE_READY){bufferingSince=0;if(!pendingResume)setStatus("");} if(state==Player.STATE_ENDED){saveProgress(true);setStatus("Finished");} }
            @Override public void onTracksChanged(Tracks tracks) { autoEnglish(tracks); }
            @Override public void onPlayerError(PlaybackException error) {
                int code=error.errorCode;
                if(code==PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW||code==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED||code==PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT||code==PlaybackException.ERROR_CODE_IO_UNSPECIFIED) retry("Connection interrupted.");
                else { if(current!=null)failed.add(current.key()); setStatus("This stream could not play ("+error.getErrorCodeName()+"). Try another channel or reselect it to retry."); filter(false); updateButtons(); }
            }
        });
    }
    private void choose(Channel channel,boolean immediate) {
        if(destroyed)return; saveProgress(false); streamGeneration++; retries=0; bufferingSince=0; pendingResume=false; current=channel; restorePosition=0; restorePlaying=true;
        captionMode=store.preferences.getString("captionMode","auto"); title.setText(channel.name); updateButtons(); store.preferences.edit().putString("lastChannel",channel.key()).apply();
        if(player==null||!started)return;
        PlaybackHistory progress=store.history(channel);
        if(!immediate&&progress!=null&&progress.canResume()) {
            player.stop(); pendingResume=true;
            new AlertDialog.Builder(this).setTitle(channel.name).setMessage("Resume from "+PlaybackHistory.time(progress.position)+"?")
                .setPositiveButton("Resume",(dialog,which)->{pendingResume=false;prepare(channel,progress.position,true);})
                .setNegativeButton("Start over",(dialog,which)->{pendingResume=false;store.progress(channel,0,progress.duration,false,true);prepare(channel,0,true);})
                .setOnCancelListener(dialog->{pendingResume=false;prepare(channel,progress.position,false);}).show();
        } else prepare(channel,0,true);
    }
    private void prepare(Channel channel,long position,boolean play) {
        if(player==null)return;
        DefaultMediaSourceFactory factory=mediaFactory(channel);
        MediaItem.Builder item=new MediaItem.Builder().setUri(channel.url).setMediaId(channel.key()); if(!channel.mimeType.isEmpty())item.setMimeType(channel.mimeType);
        player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setPreferredTextLanguages("en","eng").setSelectUndeterminedTextLanguage(false).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,"off".equals(captionMode)).build());
        player.setMediaSource(factory.createMediaSource(item.build()),position); lastChannels.opened(channel); updateButtons(); player.prepare(); player.setPlayWhenReady(play); setStatus("Opening stream…");
    }
    private DefaultMediaSourceFactory mediaFactory(Channel channel) {
        DefaultHttpDataSource.Factory http=new DefaultHttpDataSource.Factory().setUserAgent("Mozilla/5.0").setConnectTimeoutMs(15000).setReadTimeoutMs(15000).setAllowCrossProtocolRedirects(true).setDefaultRequestProperties(channel.headers);
        return new DefaultMediaSourceFactory(new DefaultDataSource.Factory(this,http)).setLoadErrorHandlingPolicy(new DefaultLoadErrorHandlingPolicy(3));
    }
    private boolean matchesCurrent() { return player!=null&&current!=null&&player.getCurrentMediaItem()!=null&&player.getCurrentMediaItem().mediaId.equals(current.key()); }
    private void saveProgress(boolean ended) { if(player!=null&&current!=null&&matchesCurrent()&&!pendingResume&&!player.isCurrentMediaItemLive()&&player.isCurrentMediaItemSeekable()&&player.getDuration()!=C.TIME_UNSET&&player.getDuration()>0)store.progress(current,player.getCurrentPosition(),player.getDuration(),ended,false); }
    private void retry(String reason) {
        if(player==null||current==null||pendingResume)return;
        if(retries>=3) { failed.add(current.key()); player.pause(); setStatus("The stream is unavailable after three retries. Select it again to retry."); filter(false); return; }
        retries++; int generation=streamGeneration; setStatus(reason+" Reconnecting ("+retries+"/3)…");
        handler.postDelayed(()->{ if(destroyed||!started||player==null||generation!=streamGeneration)return; if(player.isCurrentMediaItemLive())player.seekToDefaultPosition(); player.prepare(); player.play(); },1500L*retries);
    }
    private void playLast() { Channel target=lastChannels.target(current); if(target==null){toast("Play another channel first.");return;} choose(target,true); }
    private void updateButtons() { Channel last=lastChannels.target(current); lastButton.setEnabled(last!=null); lastButton.setContentDescription(last==null?"Last channel unavailable":"Last: "+last.name); favoriteButton.setEnabled(current!=null); favoriteButton.setText(current!=null&&store.favorite(current)?"★":"☆"); refreshButton.setEnabled(!loading); }
    private void toggleFavorite(Channel channel) { store.toggleFavorite(channel); updateButtons(); filter(false); }
    private void setStatus(String message) { status.setText(message); status.setVisibility(message.isEmpty()?View.GONE:View.VISIBLE); }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private int dp(int value) { return ChannelAdapter.dp(this,value); }

    private void setTab(String value) { tab=value; filter(true); }
    private void filter(boolean focus) {
        int generation=filterGeneration.incrementAndGet(); String query=search.getText().toString().trim().toLowerCase(Locale.ROOT); String selectedTab=tab; boolean hide=hideFailed.isChecked(); Set<String> failures=new HashSet<>(failed); List<Channel> all=channels; Map<String,PlaybackHistory> history=store.history();
        for(int id:new int[]{R.id.tab_all,R.id.tab_favorites,R.id.tab_history})findViewById(id).setActivated((id==R.id.tab_all&&tab.equals("all"))||(id==R.id.tab_favorites&&tab.equals("favorites"))||(id==R.id.tab_history&&tab.equals("history")));
        io.execute(()->{
            List<Channel> candidates=all;
            if(selectedTab.equals("history")) { Map<String,Channel> available=new HashMap<>(); for(Channel channel:all)available.put(channel.key(),channel); candidates=PlaybackHistory.visible(history,available); }
            List<Channel> visible=new ArrayList<>();
            for(Channel channel:candidates) {
                if(!query.isEmpty()&&!channel.name.toLowerCase(Locale.ROOT).contains(query)&&!channel.group.toLowerCase(Locale.ROOT).contains(query))continue;
                if(selectedTab.equals("favorites")&&!store.favorite(channel))continue;
                if(hide&&failures.contains(channel.key()))continue;
                visible.add(channel);
            }
            handler.post(()->{ if(destroyed||generation!=filterGeneration.get())return; adapter.submit(visible,selectedTab.equals("history")); count.setText(visible.size()+" channels"+(selectedTab.equals("history")?" · last episode per series":"")); empty.setVisibility(visible.isEmpty()?View.VISIBLE:View.GONE); empty.setText(selectedTab.equals("favorites")?"Star a channel or hold Select to add a favorite.":selectedTab.equals("history")?"Watched movies and episodes appear here.":"No matching channels."); if(focus)focusChannels(); });
        });
    }
    private void focusChannels() { channelList.post(()->{ RecyclerView.ViewHolder holder=channelList.findViewHolderForAdapterPosition(0); if(holder instanceof ChannelAdapter.Holder)((ChannelAdapter.Holder)holder).label.requestFocus(); else channelList.requestFocus(); }); }
    private void playlistDialog() {
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(20),dp(12),dp(20),0);
        EditText input=new EditText(this); input.setSingleLine(true); input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI); input.setHint("https://provider.example/playlist.m3u"); input.setText(store.preferences.getString("playlistUrl","")); input.setSelectAllOnFocus(true); layout.addView(input);
        TextView help=new TextView(this); help.setText("Enter your own M3U playlist or direct stream link. The Fire TV phone app can help with typing. Favorites and history are saved on this device."); help.setPadding(0,dp(12),0,dp(12)); layout.addView(help);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Your playlist").setView(layout).setPositiveButton("Load",null).setNegativeButton("Cancel",null).create();
        dialog.setOnShowListener(which->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view->{ String url=input.getText().toString().trim(); if(!PlaylistParser.isHttp(url)){input.setError("Enter an HTTP or HTTPS URL.");return;} dialog.dismiss(); loadPlaylist(url,false); })); dialog.show();
    }
    private void loadPlaylist(String url,boolean refresh) {
        int generation=++loadGeneration; loading=true; updateButtons(); setStatus("Loading playlist…");
        PlaylistProgress progress=new PlaylistProgress();beginPlaylistProgress(progress);
        io.execute(()->{
            try { List<Channel> loaded=PlaylistLoader.load(url,progress); if(destroyed||generation!=loadGeneration)return; store.writePlaylist(loaded,progress);
                handler.post(()->{ if(destroyed||generation!=loadGeneration)return; loading=false;endPlaylistProgress(); store.preferences.edit().putString("playlistUrl",url).apply(); channels=loaded; failed.clear(); filter(false); updateButtons(); setStatus("Playlist loaded."); if(!refresh||current==null)choose(channels.get(0),false); });
            } catch(Exception error) { handler.post(()->{ if(destroyed||generation!=loadGeneration)return; loading=false;endPlaylistProgress(); updateButtons(); setStatus(error instanceof IllegalArgumentException?error.getMessage():"Could not load the playlist. Check your network, provider URL, and account."); }); }
        });
    }
    private void setFullscreen(boolean value) {
        fullscreen=value; sidebar.setVisibility(value?View.GONE:View.VISIBLE); toolbar.setVisibility(value?View.GONE:View.VISIBLE); hint.setVisibility(value?View.GONE:View.VISIBLE); findViewById(R.id.root).setPadding(value?0:dp(20),value?0:dp(20),value?0:dp(20),value?0:dp(20));
        if(value) { playerView.requestFocus(); playerView.showController(); toast("Menu switches to the last channel. Back opens channels."); } else focusChannels();
    }
    @Override public void onBackPressed() { if(fullscreen){setFullscreen(false);return;} new AlertDialog.Builder(this).setTitle("Close IPTV?").setMessage("Playback will stop. Your favorites and movie progress are saved.").setPositiveButton("Close",(dialog,which)->finish()).setNegativeButton("Keep watching",null).show(); }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        int key=event.getKeyCode();
        if(key==KeyEvent.KEYCODE_MENU||key==KeyEvent.KEYCODE_LAST_CHANNEL) { if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0)playLast(); return true; }
        if(player!=null&&(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE||key==KeyEvent.KEYCODE_MEDIA_PLAY||key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_STOP||key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD||key==KeyEvent.KEYCODE_MEDIA_REWIND)) {
            if(event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()==0) {
                if(key==KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) { if(player.getPlayWhenReady())player.pause(); else player.play(); }
                else if(key==KeyEvent.KEYCODE_MEDIA_PLAY)player.play();
                else if(key==KeyEvent.KEYCODE_MEDIA_PAUSE||key==KeyEvent.KEYCODE_MEDIA_STOP){player.pause();saveProgress(false);}
                else if(player.isCurrentMediaItemSeekable()){if(key==KeyEvent.KEYCODE_MEDIA_FAST_FORWARD)player.seekForward();else player.seekBack();}
                else toast("This live stream cannot seek.");
            } return true;
        } return super.dispatchKeyEvent(event);
    }
    private void applyVolume() { if(player!=null)player.setVolume(store.preferences.getBoolean("muted",false)?0:Math.max(0,Math.min(1,store.preferences.getFloat("volume",1)))); }
    private void volumeDialog() {
        LinearLayout layout=new LinearLayout(this); layout.setOrientation(LinearLayout.VERTICAL); layout.setPadding(dp(20),dp(12),dp(20),dp(12));
        SeekBar slider=new SeekBar(this); slider.setMax(100); slider.setProgress(Math.round(store.preferences.getFloat("volume",1)*100)); slider.setContentDescription("Playback volume"); layout.addView(slider,new LinearLayout.LayoutParams(-1,dp(56)));
        CheckBox mute=new CheckBox(this); mute.setText("Mute"); mute.setChecked(store.preferences.getBoolean("muted",false)); layout.addView(mute);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onStartTrackingTouch(SeekBar bar){} public void onStopTrackingTouch(SeekBar bar){} public void onProgressChanged(SeekBar bar,int progress,boolean user){if(user){store.preferences.edit().putFloat("volume",progress/100f).apply();applyVolume();}} });
        mute.setOnCheckedChangeListener((button,checked)->{store.preferences.edit().putBoolean("muted",checked).apply();applyVolume();}); new AlertDialog.Builder(this).setTitle("Playback volume").setView(layout).setPositiveButton("Done",null).show();
    }
    private boolean english(Format format) { String language=format.language==null?"":format.language.toLowerCase(Locale.ROOT); return language.equals("en")||language.equals("eng")||language.startsWith("en-")||format.label!=null&&format.label.toLowerCase(Locale.ROOT).contains("english"); }
    private void autoEnglish(Tracks tracks) {
        if(player==null||!captionMode.equals("auto"))return; Tracks.Group best=null; int bestIndex=-1; boolean forced=false;
        for(Tracks.Group group:tracks.getGroups())if(group.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<group.length;i++) { Format format=group.getTrackFormat(i); boolean isForced=(format.selectionFlags&C.SELECTION_FLAG_FORCED)!=0; if(group.isTrackSupported(i)&&english(format)&&(best==null||isForced&&!forced)){best=group;bestIndex=i;forced=isForced;} }
        if(best!=null) { TrackSelectionParameters parameters=player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(new TrackSelectionOverride(best.getMediaTrackGroup(),bestIndex)).build(); if(!parameters.equals(player.getTrackSelectionParameters()))player.setTrackSelectionParameters(parameters); }
    }
    private void subtitleDialog() {
        if(player==null)return; List<String> names=new ArrayList<>(); List<Tracks.Group> groups=new ArrayList<>(); List<Integer> indexes=new ArrayList<>(); names.add("Auto English (prefer foreign-dialogue subtitles)"); names.add("Off");
        int checked=captionMode.equals("off")?1:0;
        for(Tracks.Group group:player.getCurrentTracks().getGroups())if(group.getType()==C.TRACK_TYPE_TEXT)for(int i=0;i<group.length;i++) {
            Format format=group.getTrackFormat(i); String name=format.label!=null?format.label:format.language!=null?format.language:"Subtitle track "+(names.size()-1); if((format.selectionFlags&C.SELECTION_FLAG_FORCED)!=0)name+=" · foreign dialogue"; if(!group.isTrackSupported(i))name+=" (unsupported)"; names.add(name); groups.add(group); indexes.add(i); if(captionMode.equals("manual")&&group.isTrackSelected(i))checked=names.size()-1;
        }
        names.add("Load an SRT / VTT subtitle file…");
        new AlertDialog.Builder(this).setTitle("Subtitles").setSingleChoiceItems(names.toArray(new String[0]),checked,(dialog,which)->{
            if(which==names.size()-1){dialog.dismiss();openSubtitleFile();return;}
            if(which<2) { captionMode=which==0?"auto":"off"; store.preferences.edit().putString("captionMode",captionMode).apply(); player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().clearOverridesOfType(C.TRACK_TYPE_TEXT).setTrackTypeDisabled(C.TRACK_TYPE_TEXT,which==1).setPreferredTextLanguages("en","eng").build()); if(which==0)autoEnglish(player.getCurrentTracks()); }
            else { Tracks.Group group=groups.get(which-2); int index=indexes.get(which-2); if(!group.isTrackSupported(index)){toast("This subtitle format is not supported by this device.");return;} captionMode="manual"; player.setTrackSelectionParameters(player.getTrackSelectionParameters().buildUpon().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,false).setOverrideForType(new TrackSelectionOverride(group.getMediaTrackGroup(),index)).build()); } dialog.dismiss();
        }).setNeutralButton("About",(dialog,which)->new AlertDialog.Builder(this).setTitle("English subtitles").setMessage("Auto English uses subtitles supplied with the stream, preferring a foreign-dialogue track. A subtitle file can also be loaded if your device has a file picker. Local AI speech translation is not included in this Fire TV preview; it needs a native ARM engine and performance testing on your Stick.").setPositiveButton("OK",null).show()).setNegativeButton("Close",null).show();
    }
    private void openSubtitleFile() {
        if(current==null)return;
        try { Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION); intent.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/vtt","application/x-subrip","application/octet-stream"}); startActivityForResult(intent,41); }
        catch(ActivityNotFoundException error) { toast("No subtitle file picker is installed. You can still use subtitle tracks supplied with the stream."); }
    }
    @Override protected void onActivityResult(int request,int result,@Nullable Intent data) {
        super.onActivityResult(request,result,data); if(request!=41||result!=RESULT_OK||data==null||data.getData()==null||player==null||current==null)return; Uri uri=data.getData();
        try { getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(SecurityException ignored) {}
        Channel selected=current; int generation=streamGeneration;
        io.execute(()->{
            try(InputStream input=getContentResolver().openInputStream(uri); ByteArrayOutputStream output=new ByteArrayOutputStream()) {
                if(input==null)throw new IllegalArgumentException("No subtitle data."); byte[] buffer=new byte[8192]; int n;
                while((n=input.read(buffer))!=-1) { output.write(buffer,0,n); if(output.size()>5*1024*1024)throw new IllegalArgumentException("Subtitle file exceeds 5 MB."); }
                String text=new String(output.toByteArray(),StandardCharsets.UTF_8).replace("\uFEFF","").trim(); String mime=text.startsWith("WEBVTT")?MimeTypes.TEXT_VTT:MimeTypes.APPLICATION_SUBRIP; if(!text.contains("-->"))throw new IllegalArgumentException("Choose an SRT or WebVTT subtitle file.");
                handler.post(()->{ if(destroyed||player==null||generation!=streamGeneration||current!=selected)return; MediaItem item=player.getCurrentMediaItem(); if(item==null)return; long position=player.getCurrentPosition(); boolean playing=player.getPlayWhenReady(); MediaItem.SubtitleConfiguration subtitle=new MediaItem.SubtitleConfiguration.Builder(uri).setMimeType(mime).setLanguage("en").setLabel("Subtitle file").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build(); captionMode="auto"; player.setMediaSource(mediaFactory(selected).createMediaSource(item.buildUpon().setSubtitleConfigurations(Collections.singletonList(subtitle)).build()),position); player.prepare(); player.setPlayWhenReady(playing); });
            } catch(Exception error) { handler.post(()->{ if(!destroyed)toast(error instanceof IllegalArgumentException?error.getMessage():"Could not load the subtitle file."); }); }
        });
    }

}
