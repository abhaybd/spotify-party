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

    private void sessionTask(Socket s) {
        BufferedReader in;
        PrintStream out;
        try {
            in = new BufferedReader(new InputStreamReader(s.getInputStream()));
            out = new PrintStream(s.getOutputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        Gson gson = new Gson();
        var req = gson.fromJson(in, InitialRequest.class);
        System.out.println("Received request: " + req.toString());
        if (req.create) {
            UUID uuid = UUID.randomUUID();
            Party p = new Party(new Member(s, in, out));
            partyMap.put(uuid, p);
            try {
                while (!Thread.interrupted()) {
                    String line = in.readLine();
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
                    toRemove.forEach(p.members::remove);
                }
            } catch (IOException e) {
                partyMap.remove(uuid);
                for (Member m : p.members) {
                    try {
                        m.socket.close();
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            }
        } else if (req.id != null && partyMap.containsKey(UUID.fromString(req.id))) {
            UUID uuid = UUID.fromString(req.id);
            partyMap.get(uuid).members.add(new Member(s, in, out));
        } else {
            try {
                out.println("ERROR 400");
                out.flush();
                s.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
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
