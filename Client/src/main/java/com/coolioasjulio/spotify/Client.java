package com.coolioasjulio.spotify;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class Client {
    public static void main(String[] args) {
        Client c = new Client();
        c.start();
    }

    private PartyManager partyManager;
    private MusicManager musicManager;
    private final Object managerLock = new Object();
    private Thread musicThread;
    private Thread memberMonitorThread;
    private ClientGUI gui;

    public Client() {
        Auth.authenticate();
    }

    public void start() {
        gui = new ClientGUI();

        gui.joinPartyButton.addActionListener(this::joinParty);
        gui.createPartyButton.addActionListener(this::createParty);
        gui.leavePartyButton.addActionListener(this::leaveParty);
        gui.endPartyButton.addActionListener(this::leaveParty);

        JFrame frame = new JFrame("Spotify Party");
        frame.setContentPane(gui.mainPanel);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.pack();
        frame.setVisible(true);
    }

    private void musicTask() {
        while (!Thread.interrupted()) {
            synchronized (managerLock) {
                if (partyManager != null && musicManager != null) {
                    try {
                        boolean running;
                        if (partyManager.isHost()) {
                            running = musicManager.pushMusicState();
                        } else {
                            running = musicManager.pullMusicState();
                        }
                        if (!running) {
                            new Thread(() -> JOptionPane.showMessageDialog(gui.mainPanel,
                                    "The party has ended!", "Info", JOptionPane.INFORMATION_MESSAGE)).start();
                            leaveParty(null);
                            break;
                        }
                    } catch (RuntimeException e) {
                        new Thread(() -> JOptionPane.showMessageDialog(gui.mainPanel,
                                "An error occurred! Restart the program!", "Error", JOptionPane.ERROR_MESSAGE)).start();
                        leaveParty(null);
                        break;
                    }
                }
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void memberMonitorTask() {
        while (!Thread.interrupted()) {
            boolean shouldCheck;
            synchronized (managerLock) {
                shouldCheck = partyManager != null && partyManager.isHost();
            }
            try {
                if (shouldCheck) {
                    String line = partyManager.getIn().readLine();
                    int i = Integer.parseInt(line.strip());
                    gui.numMembersLabel.setText("Members: " + i);
                }
            } catch (IOException | NullPointerException | NumberFormatException ignored) {
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    private void startMemberMonitorTask() {
        if (memberMonitorThread == null || !memberMonitorThread.isAlive()) {
            memberMonitorThread = new Thread(this::memberMonitorTask);
            memberMonitorThread.setDaemon(true);
            memberMonitorThread.start();
        }
    }

    private void stopMemberMonitorTask() {
        if (memberMonitorThread != null) {
            memberMonitorThread.interrupt();
        }
    }

    private void startMusicTask() {
        if (musicThread == null || !musicThread.isAlive()) {
            musicThread = new Thread(this::musicTask);
            musicThread.setDaemon(true);
            musicThread.start();
        }
    }

    private void stopMusicTask() {
        if (musicThread != null) {
            musicThread.interrupt();
        }
    }

    private void disableAllButtons() {
        gui.leavePartyButton.setEnabled(false);
        gui.createPartyButton.setEnabled(false);
        gui.joinPartyButton.setEnabled(false);
        gui.endPartyButton.setEnabled(false);
    }

    private void disableExcept(JButton button) {
        disableAllButtons();
        button.setEnabled(true);
    }

    private void enableAllButtons() {
        gui.leavePartyButton.setEnabled(true);
        gui.createPartyButton.setEnabled(true);
        gui.joinPartyButton.setEnabled(true);
        gui.endPartyButton.setEnabled(true);
    }

    private void leaveParty(ActionEvent e) {
        enableAllButtons();
        stopMusicTask();
        stopMemberMonitorTask();
        synchronized (managerLock) {
            if (partyManager != null) {
                partyManager.close();
                musicManager = null;
                partyManager = null;
            }
        }
        gui.connectionStatusLabel.setText("Not Connected");
        gui.numMembersLabel.setText("Members: 0");
    }

    private void createParty(ActionEvent e) {
        boolean success = false;
        synchronized (managerLock) {
            if (partyManager == null) {
                partyManager = PartyManager.createParty();
                success = partyManager != null;
                if (success) {
                    musicManager = new MusicManager(partyManager);
                    gui.joinCodeCreateField.setText(partyManager.getId());
                }
            }
        }
        if (success) {
            startMusicTask();
            startMemberMonitorTask();
            disableExcept(gui.endPartyButton);
        } else {
            JOptionPane.showMessageDialog(gui.mainPanel, "There was an error contacting the server!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void joinParty(ActionEvent e) {
        try {
            String id = gui.joinCodeJoinField.getText();
            PartyManager pm = PartyManager.joinParty(id);
            if (pm != null) {
                disableExcept(gui.leavePartyButton);
                synchronized (managerLock) {
                    if (partyManager == null) {
                        partyManager = pm;
                        musicManager = new MusicManager(partyManager);
                        gui.joinCodeCreateField.setText(partyManager.getId());
                    }
                }
                startMusicTask();
                gui.connectionStatusLabel.setText("Connected!");
            } else {
                JOptionPane.showMessageDialog(gui.mainPanel, "Invalid party code!", "Error", JOptionPane.ERROR_MESSAGE);
            }
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(gui.mainPanel, "There was an error contacting the server!", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
