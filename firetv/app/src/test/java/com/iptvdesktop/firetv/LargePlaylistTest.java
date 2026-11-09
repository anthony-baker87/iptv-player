package com.iptvdesktop.firetv;

import org.junit.Test;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.Assert.*;

public class LargePlaylistTest {
    @Test public void loadsOverTwentyMegabytesAndFiftyThousandChannelsOverHttp() throws Exception {
        final int total=55001;
        ExecutorService worker=Executors.newSingleThreadExecutor();
        try(ServerSocket server=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) {
            Future<Long> sent=worker.submit(()->{
                try(Socket socket=server.accept()) {
                    BufferedReader request=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                    String line;while((line=request.readLine())!=null&&!line.isEmpty()) { /* Read request without logging its URL. */ }
                    BufferedWriter out=new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(),StandardCharsets.UTF_8));
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/x-mpegurl\r\nConnection: close\r\n\r\n#EXTM3U\n");
                    String logo=new String(new char[480]).replace('\0','x');long bytes=8;
                    for(int i=0;i<total;i++) {
                        String entry="#EXTINF:-1 tvg-logo=\""+logo+"\" group-title=\"Series\",Episode "+i+"\nhttps://example.test/"+i+".mp4\n";
                        out.write(entry);bytes+=entry.length();
                    }
                    out.flush();return bytes;
                }
            });
            PlaylistProgress progress=new PlaylistProgress();
            List<Channel> result=PlaylistLoader.load("http://127.0.0.1:"+server.getLocalPort()+"/playlist",progress);
            assertEquals(-1,progress.percent());assertTrue(progress.text(0).contains("55,001 channels"));
            assertEquals(total,result.size());assertEquals("Episode 55000",result.get(total-1).name);
            assertTrue(sent.get(30,TimeUnit.SECONDS)>20L*1024*1024);
        } finally {worker.shutdownNow();}
    }
    @Test public void stopsOnAnUnboundedMalformedLine() throws Exception {
        Reader endless=new Reader(){public int read(char[] b,int off,int len){java.util.Arrays.fill(b,off,off+len,'x');return len;}public void close(){}};
        try {PlaylistParser.parse(endless,"https://example.test/list");fail("Should reject oversized line");}
        catch(IllegalArgumentException expected){assertTrue(expected.getMessage().contains("64 KB"));}
    }
    @Test public void readsHlsWithoutDownloadingTheWholeLiveManifest() throws Exception {
        Reader input=new StringReader("#EXTM3U\n#EXT-X-TARGETDURATION:10\n#EXTINF:10,\nsegment.ts\n");
        assertEquals("application/x-mpegURL",PlaylistParser.parse(input,"https://example.test/live").get(0).mimeType);
    }
}
