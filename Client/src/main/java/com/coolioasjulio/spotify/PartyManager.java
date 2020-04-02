package com.coolioasjulio.spotify;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class PartyManager {
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 5000;

    private static class InitialRequest {
        private boolean create;
        private String id;
    }

    public static PartyManager createParty() {
        var manager = new PartyManager(true);
        var req = new InitialRequest();
        req.create = true;
        manager.out.println(new Gson().toJson(req));
        manager.out.flush();
        return manager;
    }

    public static PartyManager joinParty(String code) {
        var manager = new PartyManager(false);
        var req = new InitialRequest();
        req.create = false;
        req.id = code;
        return manager;
    }

    private boolean isHost;
    private BufferedReader in;
    private PrintStream out;
    private PartyManager(boolean isHost) {
        try {
            this.isHost = isHost;
            Socket s = new Socket(HOSTNAME, PORT);
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintStream(new PrintStream(s.getOutputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isHost() {
        return isHost;
    }

    public BufferedReader getIn() {
        return in;
    }

    public PrintStream getOut() {
        return out;
    }
}
