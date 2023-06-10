package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

public abstract class AbstractMacroAction<A> implements MacroAction<A>{

    protected GameStateNode<A> gameStateNode;

    protected Empire game;

    protected int playerId;

    protected Logger log;

    protected final boolean simulation;

    public AbstractMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        this.gameStateNode = gameStateNode;
        this.game = (Empire) gameStateNode.getGame();
        this.playerId = playerId;
        this.log = log;
        this.simulation = simulation;
    }

}
