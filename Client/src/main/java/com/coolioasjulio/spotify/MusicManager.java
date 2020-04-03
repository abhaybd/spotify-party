package com.coolioasjulio.spotify;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;

import java.io.IOException;

public class MusicManager {
    private PartyManager manager;
    private Gson gson;
    private boolean paused;

    public MusicManager(PartyManager manager) {
        this.manager = manager;
        gson = new Gson();
    }

    private Result getPlaybackInfo() throws IOException, SpotifyWebApiException {
        long before = System.currentTimeMillis();
        var info = Auth.getAPI().getInformationAboutUsersCurrentPlayback().build().execute();
        long after = System.currentTimeMillis();
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

    private class Result {
        private CurrentlyPlayingContext context;
        private long oneWayLatency;
    }

    /**
     * This doesn't prompt the server, so it should be called fairly regularly.
     * It will block until data is read and processed.
     */
    public boolean pullMusicState() {
        if (manager.isHost()) return false;

        try {
            String line = manager.getIn().readLine();
            MusicState state = gson.fromJson(line, MusicState.class);
            if (line == null) return false;

            Result r = getPlaybackInfo();
            if (r == null) return true;
            var info = r.context;
            // If we're more than 50ms out of sync or we're pausing, resync
            // there's theoretically a 50ms delay on unpausing, but I doubt that'll be an issue
            int currProgress = info.getProgress_ms();
            int forwardedPos = (int) (state.songPos + info.getTimestamp() - state.timestamp);
            System.out.printf("%d - Desync: %d, oneWayLatency: %dms\n", System.currentTimeMillis(), (currProgress - forwardedPos), r.oneWayLatency);
            if (!state.isPaused && (Math.abs(currProgress - forwardedPos) > 1500 || paused)) {
                paused = false;
                String uri = Auth.getAPI().getTrack(state.songID).build().execute().getUri();
                long currTime = System.currentTimeMillis();
                int posMs = forwardedPos + (int)(currTime - info.getTimestamp() + r.oneWayLatency+800);
                Auth.getAPI().startResumeUsersPlayback()
                        .uris(JsonParser.parseString(String.format("[\"%s\"]", uri)).getAsJsonArray())
                        .position_ms(posMs)
                        .build().execute();
            } else if (state.isPaused) {
                Auth.getAPI().pauseUsersPlayback().build().execute();
                paused = true;
            }
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
            String json = gson.toJson(state);
            manager.getOut().println(json);
            manager.getOut().flush();
            return !manager.getOut().checkError();
        } catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
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
