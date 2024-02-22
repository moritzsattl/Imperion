package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.agent.Imperion;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.map.Position;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireProductionState;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnitType;

import java.util.Map;

/**
 * This class contains different heuristic approaches to determine a winning game state,
 * which is used after the MCTS is finished, in order to backpropagate the results
 */
public class Heuristics {

    private static final double MAX_GAME_TIME_MS = 300000.0;

    /**
     * A game state is considered a winner, when it occupies more than half of the cities visible && ...
     */
    public static boolean isWinner(Empire game, int playerId) {

        //logHeuristics(game, playerId);
        // gameDurationMs = current game duration + game time past due to game.advance(...)
        var gameDurationMs = Imperion.GAME_DURATION_MS + game.getGameClock().getGameDurationMs();

        Long occupation_ratio = Heuristics.cityOccupationRatio(game, playerId);
        if(occupation_ratio == null || occupation_ratio <= 0.5) return false;

        // Does not work, because simulations ends in partial infornation exception
        // IMPORTANT: This heuristic won't work if the map is bigger/smaller than then empire map (30 x 30),
        // in that case, just remove line below
        // Imperion.logger.info(mapDiscoveryRatio(game, playerId));
        // return mapDiscoveryRatio(game, playerId) > mapDiscoveryThreshold(gameDurationMs);

        return unitProductionTotalTime(game, playerId) > unitProductionTotalTimeThreshold(gameDurationMs);
    }

    private static Long cityOccupationRatio(Empire game, int playerId) {
        // Visible Cities
        var visibleCities = game.getCitiesByPosition().values();
        var playerCityCount = visibleCities.stream()
                .filter(city -> city.getPlayerId() == playerId)
                .count();

        // Avoid dividing by 0
        if (playerCityCount == 0) return null;

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
    private static double mapDiscoveryThreshold(Long duration) {
        // This is the threshold level
        // After one minute 20% of the map should be discovered

        double thresholdValue = 0;
        double startDelay = 10000;
        if(duration >= startDelay){
            thresholdValue = Math.min(1.0, (duration - startDelay) / Imperion.MAX_GAME_DURATION_MS * 0.5);
        }
        return thresholdValue;
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

    /**
     * TODO: Change to incorporate multiple cities
     * TODO: Needs to also take dead units into calculation
     */
    private static int unitProductionTotalTime(Empire game, int playerId){
        return game.getUnitsByPlayer(playerId).stream()
                .mapToInt(EmpireUnitType::getProductionTime)
                .sum();
    }

    private static double unitProductionTotalTimeThreshold(Long duration){
        // As time progresses a higher threshold could even be better;
        return (duration / 1000.0) * 0.5;
    }

    public static void logHeuristics(Empire game, int playerId) {
        var duration = Imperion.GAME_DURATION_MS + game.getGameClock().getGameDurationMs();

        Imperion.logger.info("Debugging Heuristics");
        Imperion.logger.info(duration);
        Imperion.logger.info("cityOccupationRatio: " + cityOccupationRatio(game, playerId) + ", cityOccupationThreshold: 0.5");
        //Imperion.logger.info("mapDiscoveryRatio: " + mapDiscoveryRatio(game, playerId) + ", mapDiscoveryThreshold: " + mapDiscoveryThreshold(duration));
        Imperion.logger.info("unitProductionTotalTime: " + unitProductionTotalTime(game, playerId) + ", unitProductionTotalTimeThreshold: " + unitProductionTotalTimeThreshold(duration));
        Imperion.logger._info_();
    }
}
