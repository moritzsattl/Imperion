package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitType;

import java.util.Map;

/**
 * At the start of a new MCTS round to calculate the next best move in the game, resetHeuristics() is called
 * The range minHeuristicValue and maxHeuristicValue are then dynamically adjusted based on ranges of
 * heuristic values encountered in the current MCTS round
 *
 * This class is used for calculating a normalized heuristic value for a given game to determine
 * whether or not this game is good or bad
 */
public class Heuristics {

    // Only increases
    private static Double minHeuristicValue = Double.MAX_VALUE;

    // Only decreases
    private static Double maxHeuristicValue = Double.MIN_VALUE;

    /**
     * Determines the normalized heuristic value
     */
    public static double determineNormalizedHeuristicValue(Empire game, int playerId) {

        // Determine heuristic value
        double value = determineHeuristicValue(game, playerId);

        // Update min and max heuristic
        if(value < minHeuristicValue) minHeuristicValue = value;
        if(value > maxHeuristicValue) maxHeuristicValue = value;

        double range = (maxHeuristicValue - minHeuristicValue);
        // Check if range is zero, which is the case at the very beginning
        var normalizedValue = (range != 0) ? (value - minHeuristicValue) / range : 0.5;

        // Normalize value
        return normalizedValue;
    }


    /**
     * Heuristic Value is determined the following value:
     * value = cityOccupationRatio * 10
     */
    private static double determineHeuristicValue(Empire game, int playerId) {
        double occupation_ratio = Heuristics.cityOccupationRatio(game, playerId);

        double unitCount = game.getUnitsByPlayer(playerId).size();

        return occupation_ratio * 10 + unitCount + mapDiscoveryRatio(game, playerId) * 100;
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
     * Resets Heuristics
     */
    public static void resetHeuristics(){
        minHeuristicValue = Double.MAX_VALUE;
        maxHeuristicValue = Double.MIN_VALUE;
    }

    private static Long unitStrengthHeuristic(Empire game, int playerId){
        // Sum the total of units
        double totalUnitStrength = game.getUnitsByPlayer(playerId).stream()
                // Map unit type to strength value
                .mapToDouble(unit -> {
                    if(unit.getUnitTypeId() == 1) return 1.;
                    if(unit.getUnitTypeId() == 2) return 0.5;
                    else return 2.0;
                })
                .sum();

        // TODO: Think about good approach

        return null;
    }

    public static void logHeuristics(Empire game, int playerId) {
        Imperion.logger.info("Debugging Heuristics");
        Imperion.logger.info("cityOccupationRatio: " + cityOccupationRatio(game, playerId));
        //Imperion.logger.info("mapDiscoveryRatio: " + mapDiscoveryRatio(game, playerId) + ", mapDiscoveryThreshold: " + mapDiscoveryThreshold(duration));
       Imperion.logger._info_();
    }
}
