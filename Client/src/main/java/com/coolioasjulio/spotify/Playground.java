package com.coolioasjulio.spotify;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import java.io.IOException;

public class Playground {
    public static void main(String[] args) throws IOException, SpotifyWebApiException {
        Auth.authenticate();

        Gson gson = new Gson();
        if (false) {
            var info = Auth.getAPI().getInformationAboutUsersCurrentPlayback().build().execute();
            var state = new MusicManager.MusicState(info.getTimestamp(), info.getProgress_ms(), !info.getIs_playing(), info.getItem().getUri());
            String json = gson.toJson(state);
            System.out.println(json);
        } else {
            var state = gson.fromJson("{\"timestamp\":1585782330707,\"songPos\":4618,\"isPaused\":false,\"songID\":\"3bjLCKsBNSFyx6Gfsb7X4h\"}", MusicManager.MusicState.class);
            try {
                long currTime = System.currentTimeMillis();
                int posMs = state.isPaused ? state.songPos : state.songPos + (int)(currTime - state.timestamp);
                Auth.getAPI().startResumeUsersPlayback()
                        .uris(JsonParser.parseString(String.format("[\"%s\"]", state.uri)).getAsJsonArray())
                        .position_ms(posMs)
                        .build().execute();
                if (state.isPaused) {
                    Auth.getAPI().pauseUsersPlayback().build().execute();
                }
            } catch (IOException | SpotifyWebApiException e) {
                e.printStackTrace();
            }
        }
    }
}
