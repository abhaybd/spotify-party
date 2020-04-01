package com.coolioasjulio.spotify;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.Socket;

public class PartyManager {
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 5000;

    public static PartyManager createParty() {
        var manager = new PartyManager();
        //TODO: create party
        return manager;
    }

    public static PartyManager joinParty(String code) {
        var manager = new PartyManager();
        //TODO: join party
        return manager;
    }

    private boolean isHost;
    private BufferedReader in;
    private PrintStream out;
    private PartyManager() {
        try {
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
