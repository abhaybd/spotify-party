package com.coolioasjulio.spotify.spotify.server;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import com.wrapper.spotify.SpotifyApi;
import com.wrapper.spotify.exceptions.SpotifyWebApiException;
import com.wrapper.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

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
    private ServerSocket serverSocket;

    public Server(int port) {
        this.port = port;
        partyMap = Collections.synchronizedMap(new HashMap<>());
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        Runtime.getRuntime().addShutdownHook(new Thread(this::closeAllSessions));
        while (!Thread.interrupted()) {
            try {
                System.out.println("Waiting for connection...");
                Socket s = serverSocket.accept();
                System.out.println("Received connection from: " + s.getInetAddress());
                launchSession(s);
                Thread.yield();
            } catch (SocketException e) {
                break;
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void closeAllSessions() {
        System.out.println("Closing all sessions!");
        try {
            serverSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        for (UUID uuid : partyMap.keySet()) {
            partyMap.get(uuid).close();
        }
    }

    private void launchSession(Socket s) {
        Thread t = new Thread(() -> sessionTask(s));
        t.start();
    }

    private void sessionHostTask(Party party) throws IOException {
        while (!Thread.interrupted()) {
            // If the host left, end the party
            if (party.host.socket.isClosed()) {
                break;
            }
            try {
                // Get info about host playback
                long before = System.currentTimeMillis();
                CurrentlyPlayingContext info = party.host.api.getInformationAboutUsersCurrentPlayback().build().execute();
                long after = System.currentTimeMillis();
                int oneWayLatency = (int) ((after - before) / 2);
                System.out.printf("Latency: %d\n", oneWayLatency);
                System.out.printf("Party: %s, pause=%b, progress=%d\n", party.uuid, !info.getIs_playing(), info.getProgress_ms());
                // perform necessary steps to sync party
                syncParty(party, info, oneWayLatency);
                // sleep between resyncs
                Thread.sleep(500);
            } catch (SpotifyWebApiException | InterruptedException e) {
                e.printStackTrace();
                break;
            }
        }
    }

    private void syncParty(Party party, CurrentlyPlayingContext info, int oneWayLatency) {
        String uri = info.getItem().getUri();
        // sync members, note which ones to remove
        List<Member> toRemove = Collections.synchronizedList(new LinkedList<>());
        List<CompletableFuture<?>> futures = null;
        if (!info.getIs_playing()) {
            futures = party.members.stream().map(m -> m.api.pauseUsersPlayback()
                    .build()
                    .executeAsync()
                    .exceptionally(e -> String.valueOf(toRemove.add(m))))
                    .collect(Collectors.toList());
        } else if (party.paused || !uri.equals(party.lastSong)) {
            // resync on pause or song edge event, or if way out of sync (if host skips)
            int songPos = info.getProgress_ms() + 2*oneWayLatency;
            futures = party.members.stream()
                    .map(m -> playAtPosAsync(m.api, uri, songPos)
                    .exceptionally(e -> String.valueOf(toRemove.add(m))))
                    .collect(Collectors.toList());
            party.lastSong = uri;
        }
        party.paused = !info.getIs_playing();
        if (futures != null) {
            // wait for all calls to finish
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            // remove members who left
            if (party.members.removeAll(toRemove)) {
                party.host.out.println(party.members.size());
                party.host.out.flush();
            }
        }
    }

    private CompletableFuture<String> playAtPosAsync(SpotifyApi api, String uri, int pos) {
        return api.startResumeUsersPlayback()
                .uris(JsonParser.parseString(String.format("[\"%s\"]", uri)).getAsJsonArray())
                .position_ms(pos)
                .build()
                .executeAsync();
    }

    private void handleCreateSession(Socket s, BufferedReader in, PrintStream out, InitialRequest req) {
        UUID uuid = UUID.randomUUID();
        out.println(uuid.toString());
        out.flush();
        Party party = new Party(uuid, new Member(s, in, out, Auth.createAPI(req.accessToken)));
        partyMap.put(uuid, party);
        System.out.printf("Party created with code: %s\nNum parties: %d\n", uuid, partyMap.size());
        try {
            sessionHostTask(party);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            partyMap.remove(uuid);
            System.out.printf("Party ended with code: %s\nNum parties: %d\n", uuid, partyMap.size());
            party.close();
        }
    }

    private void handleJoinSession(Socket s, BufferedReader in, PrintStream out, InitialRequest req) {
        UUID uuid = null;
        try {
            uuid = UUID.fromString(req.id);
        } catch (IllegalArgumentException ignored) {
        }
        if (uuid != null && partyMap.containsKey(uuid)) {
            out.println(uuid.toString());
            out.flush();
            Party party = partyMap.get(uuid);
            party.members.add(new Member(s, in, out, Auth.createAPI(req.accessToken)));
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
            handleCreateSession(s, in, out, req);
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
        private String accessToken;

        @Override
        public String toString() {
            return String.format("InitialRequest(create=%b, id=%s, token=%s)", create, id, accessToken);
        }
    }

    private static class Party {
        private UUID uuid;
        public Member host;
        public List<Member> members;
        public boolean paused = false;
        public String lastSong = null;

        public Party(UUID uuid, Member host) {
            this.uuid = uuid;
            this.host = host;
            members = Collections.synchronizedList(new ArrayList<>());
        }

        public void close() {
            host.close();
            members.forEach(Member::close);
        }
    }

    private static class Member {
        public Socket socket;
        public BufferedReader in;
        public PrintStream out;
        public SpotifyApi api;

        public Member(Socket socket, BufferedReader in, PrintStream out, SpotifyApi api) {
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.api = api;
        }

        public void close() {
            try {
                System.out.printf("Closing socket at address: %s\n", socket.getInetAddress());
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
