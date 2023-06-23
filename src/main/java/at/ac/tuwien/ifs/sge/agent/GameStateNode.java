package at.ac.tuwien.ifs.sge.agent;
import at.ac.tuwien.ifs.sge.agent.macroactions.MacroActionType;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GameStateNode<EmpireEvent> extends ImperionRealTimeGameNode<EmpireEvent> {

    private final int[] winsForPlayer;
    private int visits = 0;

    public GameStateNode(Empire game, Map<Integer, MacroActionType> actionsTaken) {
        super(game, actionsTaken);
        winsForPlayer = new int[game.getNumberOfPlayers()];
    }

    public int incrementWinsForPlayer(int playerId) {
        return ++winsForPlayer[playerId];
    }

    public int incrementVisits() {
        return ++visits;
    }

    public int getWinsForPlayer(int playerId) {
        return winsForPlayer[playerId];
    }

    public int getVisits() {
        return visits;
    }

    @Override
    public List<EmpireCity> knownOtherCities(int playerId) {
        return super.knownOtherCities(playerId);
    }

    @Override
    public String toString() {
        return "GameStateNode{" +
                "wins=" + Arrays.toString(winsForPlayer) +
                ", visits=" + visits +
                ", actionsTaken=" + actionsTaken +
                '}';
    }
}