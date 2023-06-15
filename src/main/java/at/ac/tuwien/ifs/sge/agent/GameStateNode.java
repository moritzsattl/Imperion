package at.ac.tuwien.ifs.sge.agent;
import at.ac.tuwien.ifs.sge.agent.macroactions.MacroActionType;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.game.empire.model.map.EmpireCity;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class GameStateNode<A> extends ImperionRealTimeGameNode<A> {

    private int[] winsForPlayer;
    private int visits = 0;

    public GameStateNode(RealTimeGame<A, ?> game, Map<Integer, MacroActionType> actionsTaken) {
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