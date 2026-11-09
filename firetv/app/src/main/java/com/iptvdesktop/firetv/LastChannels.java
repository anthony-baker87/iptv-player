package com.iptvdesktop.firetv;

public final class LastChannels {
    private Channel active, previous;
    public void opened(Channel channel) {
        if (channel==null || active!=null && active.url.equals(channel.url)) return;
        previous=active;active=channel;
    }
    public Channel target(Channel selected) {
        return selected!=null && active!=null && selected.url.equals(active.url) ? previous : active;
    }
}
