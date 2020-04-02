package com.coolioasjulio.spotify;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import java.io.IOException;

public class MusicManager {
    private PartyManager manager;
    private Gson gson;

    public MusicManager(PartyManager manager) {
        this.manager = manager;
        gson = new Gson();
    }

    /**
     * This doesn't prompt the server, so it should be called fairly regularly.
     * It will block until data is read and processed.
     */
    public void pullMusicState() {
        if (manager.isHost()) return;

        MusicState state = gson.fromJson(manager.getIn(), MusicState.class);
        try {
            String uri = Auth.getAPI().getTrack(state.songID).build().execute().getUri();
            var info = Auth.getAPI().getInformationAboutUsersCurrentPlayback().build().execute();
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
        } catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
        }
    }

    public void pushMusicState() {
        if (!manager.isHost()) return;
        try {
            var info = Auth.getAPI().getInformationAboutUsersCurrentPlayback().build().execute();
            var state = new MusicState(info.getTimestamp(), info.getProgress_ms(), !info.getIs_playing(), info.getItem().getId());
            String json = gson.toJson(state);
            manager.getOut().println(json);
            manager.getOut().flush();
        } catch (IOException | SpotifyWebApiException e) {
            e.printStackTrace();
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
