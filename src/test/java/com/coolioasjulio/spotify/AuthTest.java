package com.coolioasjulio.spotify;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AuthTest {
    @Test
    void getClientID() {
        assertNotNull(Auth.getClientID());
    }

    @Test
    void getClientSecret() {
        assertNotNull(Auth.getClientSecret());
    }

    @Test
    void loadClientAuth() {
        Auth.loadClientAuth(); // if it throws an exception, this will fail
    }
}