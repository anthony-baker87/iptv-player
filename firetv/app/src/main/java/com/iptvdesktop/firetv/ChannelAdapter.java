package com.iptvdesktop.firetv;

import android.content.Context;
import android.graphics.Color;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

final class ChannelAdapter extends RecyclerView.Adapter<ChannelAdapter.Holder> {
    interface Actions { void play(Channel channel); void favorite(Channel channel); }
    private final List<Channel> items = new ArrayList<>();
    private final LibraryStore store;
    private final Actions actions;
    private boolean history;
    ChannelAdapter(LibraryStore store, Actions actions) { this.store=store; this.actions=actions; }
    void submit(List<Channel> channels, boolean history) { items.clear(); items.addAll(channels); this.history=history; notifyDataSetChanged(); }
    static int dp(Context context,int value) { return Math.round(context.getResources().getDisplayMetrics().density*value); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
        Context context=parent.getContext(); LinearLayout row=new LinearLayout(context); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new RecyclerView.LayoutParams(-1,dp(context,72)));
        TextView label=new TextView(context); label.setId(View.generateViewId()); label.setTextColor(Color.WHITE); label.setTextSize(16); label.setGravity(Gravity.CENTER_VERTICAL); label.setMaxLines(3); label.setEllipsize(TextUtils.TruncateAt.END);
        label.setBackgroundResource(R.drawable.focus_item); label.setFocusable(true); label.setClickable(true);
        LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(0,-1,1); textParams.setMargins(0,dp(context,3),dp(context,4),dp(context,3)); row.addView(label,textParams);
        Button star=new Button(context); star.setId(View.generateViewId()); star.setTextSize(23); star.setTextColor(Color.rgb(255,209,102)); star.setBackgroundResource(R.drawable.focus_item);
        LinearLayout.LayoutParams starParams=new LinearLayout.LayoutParams(dp(context,48),-1); starParams.setMargins(0,dp(context,3),0,dp(context,3)); row.addView(star,starParams);
        label.setNextFocusRightId(star.getId()); star.setNextFocusLeftId(label.getId()); return new Holder(row,label,star);
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int index) {
        Channel channel=items.get(index); PlaybackHistory progress=store.history(channel);
        holder.label.setText(channel.name+(history&&progress!=null?"\n"+progress.description():"")); holder.label.setContentDescription(channel.name+". Hold Select to toggle favorite.");
        holder.label.setOnClickListener(view->actions.play(channel)); holder.label.setOnLongClickListener(view->{ actions.favorite(channel); return true; });
        boolean favorite=store.favorite(channel); holder.star.setText(favorite?"★":"☆"); holder.star.setContentDescription((favorite?"Remove ":"Add ")+channel.name+(favorite?" from favorites":" to favorites")); holder.star.setOnClickListener(view->actions.favorite(channel));
    }
    @Override public int getItemCount() { return items.size(); }
    static final class Holder extends RecyclerView.ViewHolder { final TextView label; final Button star; Holder(View view,TextView label,Button star) { super(view); this.label=label; this.star=star; } }
}
