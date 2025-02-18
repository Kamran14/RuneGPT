package com.kamrant.runegpt.service;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import javax.inject.Inject;
import java.util.EnumMap;
import java.util.Map;

public class PlayerStatsService {

    private final Client client;

    @Inject
    public PlayerStatsService(final Client client) {
        this.client = client;
    }

    public Map<Skill, Integer> getPlayerStats() {
        if (client.getGameState() != GameState.LOGGED_IN) {
            return null;
        }

        final Map<Skill, Integer> stats = new EnumMap<>(Skill.class);
        for (final Skill skill : Skill.values()) {
            stats.put(skill, client.getRealSkillLevel(skill));
        }
        return stats;
    }

    public String getPlayerStatsAsString() {
        final Map<Skill, Integer> stats = getPlayerStats();
        if (stats == null) {
            return "Player is not logged in.";
        }

        final StringBuilder statsBuilder = new StringBuilder();
        stats.forEach((skill, level) -> statsBuilder.append(skill.getName()).append(": ").append(level).append("\n"));
        return statsBuilder.toString();
    }
}
