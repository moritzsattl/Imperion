package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.Game;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.exception.EmpireMapException;

public abstract class AbstractMacroAction<A> implements MacroAction<A>{

    protected GameStateNode<A> gameStateNode;

    protected Empire game;

    protected int playerId;

    protected Logger log;

    public AbstractMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log) {
        this.gameStateNode = gameStateNode;
        this.game = (Empire) gameStateNode.getGame();
        this.playerId = playerId;
        this.log = log;
    }

}
