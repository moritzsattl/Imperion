package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * At the start of a new MCTS round to calculate the next best move in the game, resetHeuristics() is called
 * The range minHeuristicValue and maxHeuristicValue are then dynamically adjusted based on ranges of
 * heuristic values encountered in the current MCTS round
 *
 * This class is used for calculating a normalized heuristic value for a given game to determine
 * whether or not this game is good or bad
 */
public class Heuristics {

    // Heuristic value of game state at the start of MCTS for each player
    private static Map<Integer, Double> baseline = new HashMap<>();

    // Only increases
    // Keeps track of dynamic range of max heuristic value for each player
    private static Map<Integer, Double> maxHeuristicValue = new HashMap<>();

    /**
     * Determines the normalized heuristic value
     */
    public static double determineNormalizedHeuristicValue(Empire game, int playerId) {
        // Determine heuristic value
        double value = determineHeuristicValue(game, playerId);

        // If heuristic value is worse than baseline consider as loser
        if(value < baseline.get(playerId)) return 0.0;

        // Update max heuristic
        if(value > maxHeuristicValue.get(playerId)) maxHeuristicValue.put(playerId, value);

        double range = (maxHeuristicValue.get(playerId) - baseline.get(playerId));

        // If value is bigger than baseline, but range is 0 (This is the case if no better outcome than baseline is found, consider as very small winner (just in case there are no good outcomes))
        return (range != 0) ? (value - baseline.get(playerId)) / range : 0.01;
    }


    private static double determineHeuristicValue(Empire game, int playerId) {
        // ranging from 0 to 1
        double occupation_ratio = Heuristics.cityOccupationRatio(game, playerId);

        // ranging from 0 to 100
        double unitCount = game.getUnitsByPlayer(playerId).size();

        // ranging from 0 to 1
        double fightHeuristic = Heuristics.fightHeuristic(game, playerId);

        return occupation_ratio * 100 + unitCount * 10 + mapDiscoveryRatio(game, playerId) * 300 + fightHeuristic * 100;
    }

    /**
     * Returns percentage of visible enemies units health taken divided by their total health
     */
    private static double fightHeuristic(Empire game, int playerId) {
        double dmgDone = 0;
        double totalHealth = 0;

        for (int pid = 0; pid < game.getNumberOfPlayers(); pid++) {
            if(pid == playerId) continue;

            dmgDone += game.getUnitsByPlayer(pid).stream()
                    .mapToDouble(unit -> unit.getMaxHp() - unit.getHp())
                    .sum();

            totalHealth += game.getUnitsByPlayer(pid).stream()
                    .mapToDouble(EmpireUnitType::getMaxHp)
                    .sum();
        }

        if(totalHealth == 0) return 0;

        return dmgDone/totalHealth;
    }


    private static Double cityOccupationRatio(Empire game, int playerId) {
        // Visible Cities
        var visibleCities = game.getCitiesByPosition().values();
        double playerCityCount = visibleCities.stream()
                .filter(city -> city.getPlayerId() == playerId)
                .count();

        // Avoid dividing by 0
        if (playerCityCount == 0) return 0.0;

        return playerCityCount / visibleCities.size();
    }

    public static double mapDiscoveryRatio(Empire game, int playerId){
        double discoveredPositionCount = game.getBoard().getDiscoveredByPosition()
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue()[playerId])
                .map(Map.Entry::getKey)
                .count();

        double allPositionsCount = game.getBoard().getMapSize().getWidth() * game.getBoard().getMapSize().getHeight();

        return discoveredPositionCount / allPositionsCount;
    }

    /**
     * Set min and max heuristic to heuristic value of current game state
     */
    public static void resetHeuristics(Empire game){
        IntStream.range(0, game.getNumberOfPlayers()).forEach(pid -> {
            var val = determineHeuristicValue(game, pid);
            baseline.put(pid, val);
            maxHeuristicValue.put(pid, val);
        });
    }


    public static void debHeuristics(Empire game, int playerId) {
        Imperion.logger.debug("Debugging Heuristics for playerId: " + playerId);
        Imperion.logger.debug(game.getUnitsByPlayer(playerId).stream()
                .map(unit -> new AbstractMap.SimpleEntry<>(unit, unit.getPosition()))
                .collect(Collectors.toSet()));

        // ranging from 0 to 1
        double occupation_ratio = Heuristics.cityOccupationRatio(game, playerId);

        // ranging from 0 to 100
        double unitCount = game.getUnitsByPlayer(playerId).size();

        // ranging from 0 to 1
        double fightHeuristic = Heuristics.fightHeuristic(game, playerId);

        Imperion.logger.debug(occupation_ratio * 100 + " " + unitCount * 10 + " " + mapDiscoveryRatio(game, playerId) * 500 + " " + fightHeuristic * 100);

        Imperion.logger.debug(determineHeuristicValue(game, playerId));
        Imperion.logger.debug(determineNormalizedHeuristicValue(game, playerId));
        Imperion.logger.debug("Baseline " + baseline.get(playerId) + " Max " + maxHeuristicValue.get(playerId));
        Imperion.logger._debug_();
    }
}
