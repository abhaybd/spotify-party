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

    public void pullMusicState() {
        if (manager.isHost()) return;
        manager.getOut().println("StateRequest");
        manager.getOut().flush();

        MusicState state = gson.fromJson(manager.getIn(), MusicState.class);
        try {
            String uri = Auth.getAPI().getTrack(state.songID).build().execute().getUri();
            long currTime = System.currentTimeMillis();
            int posMs = state.isPaused ? state.songPos : state.songPos + (int)(currTime - state.timestamp);
            Auth.getAPI().startResumeUsersPlayback()
                    .uris(JsonParser.parseString(String.format("[\"%s\"]", uri)).getAsJsonArray())
                    .position_ms(posMs)
                    .build().execute();
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
