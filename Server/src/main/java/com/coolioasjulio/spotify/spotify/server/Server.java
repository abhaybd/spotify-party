package com.coolioasjulio.spotify.spotify.server;

import com.google.gson.Gson;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Server {
    public static void main(String[] args) {
        Server s = new Server(5000);
        try {
            s.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private int port;
    private Map<UUID, Party> partyMap;
    public Server(int port) {
        this.port = port;
        partyMap = Collections.synchronizedMap(new HashMap<>());
    }

    public void start() throws IOException {
        ServerSocket serverSocket = new ServerSocket(port);
        while (!Thread.interrupted()) {
            try {
                System.out.println("Waiting for connection...");
                Socket s = serverSocket.accept();
                System.out.println("Received connection from: " + s.getInetAddress());
                launchSession(s);
                Thread.yield();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void launchSession(Socket s) {
        Thread t = new Thread(() -> sessionTask(s));
        t.start();
    }

    private void handleCreateSession(Socket s, BufferedReader in, PrintStream out) {
        UUID uuid = UUID.randomUUID();
        out.println(uuid.toString());
        out.flush();
        Party p = new Party(new Member(s, in, out));
        partyMap.put(uuid, p);
        System.out.printf("Party created with code: %s\nNum parties: %d\n", uuid, partyMap.size());
        try {
            while (!Thread.interrupted()) {
                String line = in.readLine();
                if (line == null) break; // stream closed
                System.out.printf("%s : %s\n", p.host.socket.getInetAddress(), line);
                List<Member> toRemove = new ArrayList<>();
                for (int i = 0; i < p.members.size(); i++) {
                    Member m = p.members.get(i);
                    m.out.println(line);
                    m.out.flush();
                    if (m.out.checkError()) {
                        m.socket.close();
                        toRemove.add(m);
                    }
                }
                if (p.members.removeAll(toRemove)) {
                    out.println(p.members.size());
                    out.flush();
                }
                Thread.yield();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            partyMap.remove(uuid);
            System.out.printf("Party ended with code: %s\nNum parties: %d\n", uuid, partyMap.size());
            for (Member m : p.members) {
                try {
                    m.socket.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
    }

    private void handleJoinSession(Socket s, BufferedReader in, PrintStream out, InitialRequest req) {
        UUID uuid = null;
        try {
            uuid = UUID.fromString(req.id);
        } catch (IllegalArgumentException ignored) {}
        if (uuid != null && partyMap.containsKey(uuid)) {
            out.println(uuid.toString());
            out.flush();
            Party party = partyMap.get(uuid);
            party.members.add(new Member(s, in, out));
            party.host.out.println(party.members.size());
            party.host.out.flush();
        } else {
            error(s, out);
        }
    }

    private void sessionTask(Socket s) {
        BufferedReader in;
        PrintStream out;
        InitialRequest req;
        try {
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintStream(s.getOutputStream());
            Gson gson = new Gson();
            req = gson.fromJson(in.readLine(), InitialRequest.class);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        System.out.println("Received request: " + req.toString());
        if (req.create) {
            handleCreateSession(s, in, out);
        } else if (req.id != null) {
            handleJoinSession(s, in, out, req);
        } else {
            System.out.println("Request denied, unable to join group.");
            error(s, out);
        }
    }

    private void error(Socket s, PrintStream out) {
        try {
            out.println("ERROR 400");
            out.flush();
            s.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static class InitialRequest {
        private boolean create;
        private String id;

        @Override
        public String toString() {
            return String.format("InitialRequest(create=%b, id=%s)", create, id);
        }
    }

    private static class MusicState {
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

    private static class Party {
        public Member host;
        public List<Member> members;

        public Party(Member host) {
            this.host = host;
            members = Collections.synchronizedList(new ArrayList<>());
        }
    }

    private static class Member {
        public Socket socket;
        public BufferedReader in;
        public PrintStream out;
        public Member(Socket socket, BufferedReader in, PrintStream out) {
            this.socket = socket;;
            this.in = in;
            this.out = out;
        }
    }
}
