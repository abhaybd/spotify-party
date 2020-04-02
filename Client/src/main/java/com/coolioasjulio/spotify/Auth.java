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
import java.util.function.Consumer;

public class Auth {
    private static final int PORT = 8080;
    private static final String SCOPES = "user-read-playback-state user-modify-playback-state";
    private static final String REDIRECT_URI = String.format("http://localhost:%d", PORT);
    private static final File tokenFile = new File(System.getProperty("user.home") + File.separator + ".spotifyparty", "token");
    private static String clientID, clientSecret;
    private static String accessToken, refreshToken;
    private static SpotifyApi api;
    private static Thread refreshThread;

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

    private static void startRefreshThread() {
        if (refreshThread == null || !refreshThread.isAlive()) {
            refreshThread = new Thread(Auth::refreshTask);
            refreshThread.setDaemon(true);
            refreshThread.start();
        }
    }

    private static String getAuthCode(Consumer<URI> backup) {
        // Open the auth uri in the default browser
        var uriReq = getAPI().authorizationCodeUri().scope(SCOPES).build();
        URI uri = uriReq.execute();
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            try {
                Desktop.getDesktop().browse(uri);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else if (backup != null) {
            backup.accept(uri);
        } else {
            throw new IllegalStateException("Unable to open authentication uri!");
        }
        String code;
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
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

    private static void authenticateFromScratch(Consumer<URI> backup) {
        // Get the authorization code (this goes through the browser process and everything)
        String code = getAuthCode(backup);
        try {
            // exchange the code for the access and refresh tokens
            var cred = getAPI().authorizationCode(code).build().execute();
            accessToken = cred.getAccessToken();
            refreshToken = cred.getRefreshToken();
            getAPI().setAccessToken(accessToken);
            getAPI().setRefreshToken(refreshToken);

            // Save the refresh token to disk
            tokenFile.getParentFile().mkdirs();
            PrintStream out = new PrintStream(tokenFile);
            out.println(refreshToken);
            out.close();
        } catch (IOException | SpotifyWebApiException e) {
            throw new RuntimeException(e);
        }
    }

    public static void refresh() {
        // If we don't have a refresh token set, load from disk
        if (getAPI().getRefreshToken() == null) {
            try (Scanner in = new Scanner(new FileInputStream(tokenFile))) {
                String token = in.nextLine();
                refreshToken = token;
                getAPI().setRefreshToken(token);
            } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        // Now use the refresh token to get a new access token
        try {
            var cred = getAPI().authorizationCodeRefresh().build().execute();
            accessToken = cred.getAccessToken();
            getAPI().setAccessToken(cred.getAccessToken());
        } catch (IOException | SpotifyWebApiException e) {
            throw new RuntimeException(e);
        }
        // Periodically refresh the access token
        startRefreshThread();
    }

    public static void authenticate() {
        authenticate(null);
    }

    /**
     * Authenticate with spotify. This should be done once at startup.
     *
     * @param backup If this platform doesn't support opening webpages,
     *               then this consumer will be called with the URI to show to the user.
     *               This may be null, but an exception will be thrown instead of using the backup.
     */
    public static void authenticate(Consumer<URI> backup) {
        // If we have a cached refresh token, use it to silently get an access token
        if (tokenFile.isFile()) {
            System.out.println("Cached refresh token found! Refreshing...");
            refresh();
        } else {
            // Otherwise, go through the entire authentication process
            System.out.println("No cache found! Authenticating...");
            authenticateFromScratch(backup);
            startRefreshThread();
        }
    }
}
