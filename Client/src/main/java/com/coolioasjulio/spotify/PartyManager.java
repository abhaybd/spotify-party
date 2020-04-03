package com.coolioasjulio.spotify;

import com.google.gson.Gson;
import org.apache.commons.net.ntp.NTPUDPClient;
import org.apache.commons.net.ntp.TimeInfo;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;

public class PartyManager implements Closeable {
    private static final String HOSTNAME = "localhost";
    private static final int PORT = 5000;

    private static class InitialRequest {
        private boolean create;
        private String id;
    }

    public static PartyManager createParty() {
        PartyManager manager;
        try {
            manager = new PartyManager(true, null);
        } catch (RuntimeException e) {
            return null;
        }
        var req = new InitialRequest();
        req.create = true;
        manager.out.println(new Gson().toJson(req));
        manager.out.flush();
        try {
            manager.id = manager.in.readLine();
        } catch (IOException e) {
            manager.close();
            return null;
        }
        return manager;
    }

    public static PartyManager joinParty(String code) {
        var manager = new PartyManager(false, code);
        var req = new InitialRequest();
        req.create = false;
        req.id = code;
        manager.out.println(new Gson().toJson(req));
        manager.out.flush();
        try {
            if (!manager.id.equals(manager.in.readLine())) {
                manager.close();
                return null;
            }
        } catch (IOException e) {
            manager.close();
            throw new RuntimeException(e);
        }
        return manager;
    }

    private boolean isHost;
    private String id;
    private final Socket socket;
    private BufferedReader in;
    private PrintStream out;
    private long networkTimeOffset;

    private PartyManager(boolean isHost, String id) {
        this.isHost = isHost;
        this.id = id;
        try {
            networkTimeOffset = calculateNetworkTimeOffset();
            socket = new Socket(HOSTNAME, PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintStream(new PrintStream(socket.getOutputStream()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private long calculateNetworkTimeOffset() throws IOException {
        NTPUDPClient client = new NTPUDPClient();
        // We want to timeout if a response takes longer than 10 seconds
        client.setDefaultTimeout(1000);

        InetAddress inetAddress = InetAddress.getByName("time.google.com");
        TimeInfo timeInfo = client.getTime(inetAddress);
        timeInfo.computeDetails();
        return timeInfo.getOffset() != null ? timeInfo.getOffset() : 0;
    }

    public long getNetworkTime() {
        return System.currentTimeMillis() + networkTimeOffset;
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public boolean isHost() {
        return isHost;
    }

    public String getId() {
        return id;
    }

    public BufferedReader getIn() {
        return in;
    }

    public PrintStream getOut() {
        return out;
    }
}
