package com.coolioasjulio.spotify;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;

import java.io.IOException;

public class MusicManager {
    private PartyManager manager;
    private Gson gson;

    public MusicManager(PartyManager manager) {
        this.manager = manager;
        gson = new Gson();
    }

    private CurrentlyPlayingContext getPlaybackInfo() throws IOException, SpotifyWebApiException {
        long before = System.currentTimeMillis();
        var info = Auth.getAPI().getInformationAboutUsersCurrentPlayback().build().execute();
        long after = System.currentTimeMillis();
        if (info == null) return null;
        // Clone the info object, but average the before and after timestamps to estimate latency
        // This is necessary since the built in timestamp is broken
        return new CurrentlyPlayingContext.Builder()
                .setDevice(info.getDevice())
                .setContext(info.getContext())
                .setRepeat_state(info.getRepeat_state())
                .setShuffle_state(info.getShuffle_state())
                .setIs_playing(info.getIs_playing())
                .setItem(info.getItem())
                .setProgress_ms(info.getProgress_ms())
                .setTimestamp((before + after) / 2)
                .build();
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

            String uri = Auth.getAPI().getTrack(state.songID).build().execute().getUri();
            var info = getPlaybackInfo();
            if (info == null) return true;
            // If we're more than 50ms out of sync or we're pausing, resync
            // there's theoretically a 50ms delay on unpausing, but I doubt that'll be an issue
            if (state.isPaused || Math.abs(info.getProgress_ms() - (state.songPos + (info.getTimestamp() - state.timestamp))) > 50) {
                long currTime = System.currentTimeMillis();
                int posMs = state.isPaused ? state.songPos : state.songPos + (int)(currTime - state.timestamp);
                Auth.getAPI().startResumeUsersPlayback()
                        .uris(JsonParser.parseString(String.format("[\"%s\"]", uri)).getAsJsonArray())
                        .position_ms(posMs)
                        .build().execute();
            }
            if (state.isPaused) {
                Auth.getAPI().pauseUsersPlayback().build().execute();
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
            var info = getPlaybackInfo();
            if (info == null) return true;
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
