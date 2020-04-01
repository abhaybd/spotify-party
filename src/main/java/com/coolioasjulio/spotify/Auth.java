package com.coolioasjulio.spotify;

import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.SpotifyHttpManager;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.URI;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class Auth {
    private static final int PORT = 8080;
    private static final String REDIRECT_URI = String.format("http://localhost:%d", PORT);
    private static final File tokenFile = new File(System.getProperty("user.home") + File.separator + ".spotifyparty", "token");
    private static String clientID, clientSecret;
    private static String accessToken, refreshToken;
    private static SpotifyApi api;

    public static SpotifyApi getAPI() {
        if (api == null) {
            api = new SpotifyApi.Builder()
                    .setClientSecret(getClientSecret())
                    .setClientId(getClientID())
                    .setRedirectUri(SpotifyHttpManager.makeUri(REDIRECT_URI))
                    .build();
        }
        return api;
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
        try (Scanner in = new Scanner(Auth.class.getResourceAsStream("/client.auth"))) {
            clientID = in.nextLine();
            clientSecret = in.nextLine();
        }
    }

    public static String getAccessToken() {
        return accessToken;
    }

    public static String getRefreshToken() {
        return refreshToken;
    }

    private static void refreshTask() {
        while (!Thread.interrupted()) {
            try {
                // tokens last 1 hour, so refresh periodically
                Thread.sleep(TimeUnit.MINUTES.toMillis(45));
                Auth.refresh();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private static String getAuthCode() {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            var uriReq = getAPI().authorizationCodeUri().build();
            URI uri = uriReq.execute();
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        String code;
        try (ServerSocket serverSocket = new ServerSocket(PORT)){
            var socket = serverSocket.accept();
            var in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            String req = in.readLine();
            String[] parts = req.split(" ");
            code = parts[1].substring("/?code=".length()); // remove the param name

            var htmlFile = new Scanner(Auth.class.getResourceAsStream("/redirect.html"));
            var out = new PrintStream(socket.getOutputStream());
            // Print headers
            out.println("HTTP/1.1 200");
            out.println("Content-Type: text/html");
            out.println("Connection: close");
            out.println(); // End of headers
            while (htmlFile.hasNextLine()) {
                out.println(htmlFile.nextLine());
            }
            htmlFile.close();
            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return code;
    }

    private static void authenticateFromScratch() {
        String code = getAuthCode();
        try {
            var cred = getAPI().authorizationCode(code).build().execute();
            accessToken = cred.getAccessToken();
            refreshToken = cred.getRefreshToken();
            getAPI().setAccessToken(accessToken);
            getAPI().setRefreshToken(refreshToken);

            tokenFile.getParentFile().mkdirs();
            PrintStream out = new PrintStream(tokenFile);
            out.println(refreshToken);
            out.close();
        } catch (IOException | SpotifyWebApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static void refresh() {
        if (getAPI().getRefreshToken() == null) {
            try (Scanner in = new Scanner(new FileInputStream(tokenFile))) {
                String token = in.nextLine();
                getAPI().setRefreshToken(token);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            var cred = getAPI().authorizationCodeRefresh().build().execute();
            getAPI().setAccessToken(cred.getAccessToken());
        } catch (IOException | SpotifyWebApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static void authenticate() {
        if (tokenFile.isFile()) {
            System.out.println("Cached refresh token found! Refreshing...");
            refresh();
        } else {
            System.out.println("No cache found! Authenticating...");
            authenticateFromScratch();
        }
    }
}
