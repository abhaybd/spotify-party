package com.coolioasjulio.spotify;

public class Playground2 {
    public static void main(String[] args) {
        Auth.authenticate();
        PartyManager pm = PartyManager.createParty();
        MusicManager mm = new MusicManager(pm);
        boolean running = true;
        while (running) {
            running = mm.pushMusicState();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
