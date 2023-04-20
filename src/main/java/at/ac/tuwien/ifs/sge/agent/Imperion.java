package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.AbstractRealTimeGameAgent;
import at.ac.tuwien.ifs.sge.core.engine.communication.ActionResult;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public class Imperion extends AbstractRealTimeGameAgent {

    public static void main(String[] args) {
        var playerId = getPlayerIdFromArgs(args);
        var playerName = getPlayerNameFromArgs(args);
        var agent = new Imperion(playerId, playerName, -2);
        agent.start();
    }
    public Imperion(int playerId, String playerName, int logLevel) {
        super(Empire.class, playerId, playerName, logLevel);
    }

    @Override
    protected void onGameUpdate(Object action, ActionResult result) {

    }

    @Override
    protected void onActionRejected(Object action) {

    }

    @Override
    public void shutdown() {

    }

    @Override
    public void startPlaying() {
        super.startPlaying();
    }
}