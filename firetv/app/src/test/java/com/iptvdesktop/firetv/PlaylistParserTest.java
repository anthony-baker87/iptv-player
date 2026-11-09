package com.iptvdesktop.firetv;
import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;
public class PlaylistParserTest {
    @Test public void preservesQuotedCommasResolvesRelativeLinksAndHeaders() {
        String m3u="\uFEFF#EXTM3U\n#EXTINF:-1 tvg-id=\"42\" tvg-name=\"Name, with comma\" group-title=\"Sports\",Game, live\n#EXTVLCOPT:http-user-agent=TV Player\n../game.m3u8|Referer=https%3A%2F%2Fexample.test\n";
        List<Channel> channels=PlaylistParser.parse(m3u,"https://example.test/lists/main.m3u");
        assertEquals(1,channels.size());Channel channel=channels.get(0);
        assertEquals("Game, live",channel.name);assertEquals("https://example.test/game.m3u8",channel.url);assertEquals("Sports",channel.group);
        assertEquals("TV Player",channel.headers.get("User-Agent"));assertEquals("https://example.test",channel.headers.get("Referer"));
    }
    @Test public void recognizesHlsWithoutTreatingSegmentsAsChannels() {
        List<Channel> result=PlaylistParser.parse("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nsegment.ts\n#EXT-X-ENDLIST","https://example.test/live.m3u8");
        assertEquals(1,result.size());assertEquals("https://example.test/live.m3u8",result.get(0).url);assertEquals("application/x-mpegURL",result.get(0).mimeType);
    }
    @Test public void skipsUnsafeLinksAndDeduplicates() {
        String text="#EXTM3U\n#EXTINF:-1,Bad\njavascript:alert(1)\n#EXTINF:-1,A\nhttps://example.test/a\n#EXTINF:-1,Duplicate\nhttps://example.test/a";
        assertEquals(1,PlaylistParser.parse(text,"https://example.test/list").size());
    }
    @Test(expected=IllegalArgumentException.class) public void rejectsAccountErrorPages() {PlaylistParser.parse("<html>Access denied</html>","https://example.test/list");}
    @Test public void stableProviderIdSurvivesTokenChanges() {Channel a=PlaylistParser.parse("#EXTM3U\n#EXTINF:-1 tvg-id=\"1\",Movie\nhttps://example.test/movie.mp4?token=old","https://example.test/list").get(0);Channel b=new Channel(a.name,"https://example.test/movie.mp4?token=new",a.group,a.id,a.mimeType,a.headers);assertEquals(a.key(),b.key());}
}
