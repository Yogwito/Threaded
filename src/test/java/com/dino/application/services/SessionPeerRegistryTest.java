package com.dino.application.services;

import com.dino.domain.entities.Player;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionPeerRegistryTest {
    @Test
    void remoteAddressesExcludeLocalPeer() {
        SessionPeerRegistry registry = new SessionPeerRegistry();
        registry.register("host", new InetSocketAddress("127.0.0.1", 7000));
        registry.register("client", new InetSocketAddress("127.0.0.1", 7001));

        List<InetSocketAddress> remotes = registry.getRemoteAddresses("host");

        assertEquals(1, remotes.size());
        assertEquals(7001, remotes.getFirst().getPort());
    }

    @Test
    void expireInactivePlayersDisconnectsOnlyTimedOutPeers() throws Exception {
        SessionPeerRegistry registry = new SessionPeerRegistry();
        Map<String, Player> players = new LinkedHashMap<>();
        Player host = new Player("host", "Host", "red");
        Player remote = new Player("client", "Client", "blue");
        players.put(host.getId(), host);
        players.put(remote.getId(), remote);

        registry.register("host", new InetSocketAddress("127.0.0.1", 7000));
        registry.register("client", new InetSocketAddress("127.0.0.1", 7001));
        Thread.sleep(5);

        List<String> expired = registry.expireInactivePlayers(players, "host", true, 0);

        assertEquals(List.of("client"), expired);
        assertTrue(host.isConnected());
        assertFalse(remote.isConnected());
        assertNull(registry.getAgeMillis("client"));
    }
}
