package com.coolioasjulio.spotify;

import javax.swing.*;

public class Playground {
    public static void main(String[] args) {
        Auth.authenticate(uri -> {
            Thread t = new Thread(() -> {
                JTextArea area = new JTextArea(
                        "Unable to open web page automatically.\n" +
                                "Please copy/paste this url into your browser:\n" +
                                uri.toString());

                area.setEditable(false);
                JOptionPane.showMessageDialog(null, area, "", JOptionPane.INFORMATION_MESSAGE);
            });
            t.setDaemon(true);
            t.start();
        });
    }
}
