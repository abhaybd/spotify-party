package com.coolioasjulio.spotify;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;

import java.io.IOException;

public class MusicManager {
    private PartyManager manager;
    private Gson gson;
    private String lastSong;
    private boolean paused;
    private Long unPauseTime = null;

    public MusicManager(PartyManager manager) {
        this.manager = manager;
        gson = new Gson();
    }

    private Result getPlaybackInfo() throws IOException, SpotifyWebApiException {
        long before = manager.getNetworkTime();
        var info = Auth.getAPI().getInformationAboutUsersCurrentPlayback().build().execute();
        long after = manager.getNetworkTime();
        if (info == null) return null;
        // Clone the info object, but average the before and after timestamps to estimate latency
        // This is necessary since the built in timestamp is broken
        Result r = new Result();
        r.context = new CurrentlyPlayingContext.Builder()
                .setDevice(info.getDevice())
                .setContext(info.getContext())
                .setRepeat_state(info.getRepeat_state())
                .setShuffle_state(info.getShuffle_state())
                .setIs_playing(info.getIs_playing())
                .setItem(info.getItem())
                .setProgress_ms(info.getProgress_ms())
                .setTimestamp((before + after) / 2)
                .build();
        r.oneWayLatency = (after - before) / 2;
        return r;
    }

    /**
     * This doesn't prompt the server, so it should be called fairly regularly.
     * It will block until data is read and processed.
     */
    public boolean pullMusicState() {
        if (manager.isHost()) return false;

        try {
            String line = manager.getIn().readLine();
            if (line == null) return false;
            MusicState state = gson.fromJson(line, MusicState.class);
            long clientDelay = manager.getNetworkTime() - state.timestamp;
            System.out.println("Client delay: " + clientDelay);
            Result r = getPlaybackInfo();
            if (r == null) return true;
            var info = r.context;
            int hostSongPos = (int) (state.songPos + info.getTimestamp() - state.timestamp);
            String uri = Auth.getAPI().getTrack(state.songID).build().execute().getUri();
            if (state.isPaused) {
                Auth.getAPI().pauseUsersPlayback().build().execute();
            } else if (paused || !uri.equals(lastSong) || Math.abs(hostSongPos - info.getProgress_ms()) > 3000) {
                // resync on pause or song edge event, or if way out of sync (if host skips)
                long currTime = manager.getNetworkTime();
                System.out.printf("Offset: %dms, latency: %dms\n", currTime - info.getTimestamp(), r.oneWayLatency);
                int posMs = Math.max(0, hostSongPos + (int)(currTime - info.getTimestamp() + r.oneWayLatency + clientDelay));
                Auth.getAPI().startResumeUsersPlayback()
                        .uris(JsonParser.parseString(String.format("[\"%s\"]", uri)).getAsJsonArray())
                        .position_ms(posMs)
                        .build().execute();
                lastSong = uri;
            }
            paused = state.isPaused;
            return true;
        } catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    public boolean pushMusicState() {
        if (!manager.isHost()) return false;
        try {
            Result r = getPlaybackInfo();
            if (r == null) return true;
            var info = r.context;
            var state = new MusicState(info.getTimestamp(), info.getProgress_ms(), !info.getIs_playing(), info.getItem().getId());
            if (!state.songID.equals(lastSong) || (unPauseTime != null && manager.getNetworkTime() < unPauseTime)) {
                if (unPauseTime == null) {
                    unPauseTime = manager.getNetworkTime() + 1000;
                }
                lastSong = state.songID;
                paused = true;
                state.isPaused = true; // pause all members for some period of time
            } else if (unPauseTime != null && manager.getNetworkTime() >= unPauseTime) {
                unPauseTime = null;
            }
            String json = gson.toJson(state);
            manager.getOut().println(json);
            manager.getOut().flush();
            return !manager.getOut().checkError();
        } catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    private static class Result {
        private CurrentlyPlayingContext context;
        private long oneWayLatency;
    }

    static class MusicState {
        public long timestamp;
        public int songPos;
        public boolean isPaused;
        public String songID;

        public MusicState(long timestamp, int songPos, boolean isPaused, String songID) {
            this.timestamp = timestamp;
            this.songPos = songPos;
            this.isPaused = isPaused;
            this.songID = songID;
        }
    }
}
