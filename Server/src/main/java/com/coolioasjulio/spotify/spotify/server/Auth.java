package com.coolioasjulio.spotify.spotify.server;

import com.wrapper.spotify.SpotifyApi;

import java.util.Scanner;

public class Auth {
    private static String clientID;
    private static String clientSecret;

    public static SpotifyApi createAPI(String accessToken) {
        return new SpotifyApi.Builder().setAccessToken(accessToken)
                .setClientId(getClientID()).setClientSecret(getClientSecret()).build();
    }

    public static String getClientID() {
        if (clientID == null) loadClientAuth();
        return clientID;
    }

    public static String getClientSecret() {
        if (clientSecret == null) loadClientAuth();
        return clientSecret;
    }

    public static void loadClientAuth() {
        try (Scanner in = new Scanner(Auth.class.getResourceAsStream("/server.auth"))) {
            clientID = in.nextLine();
            clientSecret = in.nextLine();
        }
    }
}
