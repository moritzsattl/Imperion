package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
import java.util.List;

public abstract class AbstractMacroAction<A> implements MacroAction<A>{

    protected GameStateNode<A> gameStateNode;

    protected final List<EmpireUnit> units;

    protected Empire game;

    protected int playerId;

    protected Logger log;

    protected final boolean simulation;

    public AbstractMacroAction(GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulation) {
        this.gameStateNode = gameStateNode;
        this.game = (Empire) gameStateNode.getGame();
        this.units = game.getUnitsByPlayer(playerId);
        this.playerId = playerId;
        this.log = log;
        this.simulation = simulation;
    }



}
