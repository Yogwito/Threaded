package com.dino.presentation.flow;

import com.dino.application.services.EventBus;
import com.dino.application.services.SessionService;
import com.dino.domain.entities.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GameOverSummaryFactoryTest {
    @Test
    void buildSortsPlayersAndBuildsWinnerText() {
        SessionService session = new SessionService(new EventBus());
        Player alice = new Player("a", "Alice", "red");
        alice.setScore(12);
        Player bob = new Player("b", "Bob", "blue");
        bob.setScore(7);
        session.addPlayer(alice);
        session.addPlayer(bob);

        GameOverSummary summary = new GameOverSummaryFactory().build(session);

        assertEquals("Alice", summary.standings().getFirst().getName());
        assertEquals("Ganador: Alice (12 masa)", summary.winnerText());
        assertEquals("Duración: 0.0s", summary.totalTimeText());
    }
}
