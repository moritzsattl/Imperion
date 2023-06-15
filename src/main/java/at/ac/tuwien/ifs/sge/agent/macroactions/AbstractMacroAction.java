package at.ac.tuwien.ifs.sge.agent.macroactions;

import at.ac.tuwien.ifs.sge.agent.GameStateNode;
import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;
import at.ac.tuwien.ifs.sge.game.empire.model.units.EmpireUnit;
import java.util.List;

public abstract class AbstractMacroAction<EmpireEvent> implements MacroAction<EmpireEvent>{

    protected GameStateNode<EmpireEvent> gameStateNode;

    protected final List<EmpireUnit> units;

    protected Empire game;

    protected int playerId;

    protected Logger log;

    protected final boolean simulation;

    public AbstractMacroAction(GameStateNode<EmpireEvent> gameStateNode, int playerId, Logger log, boolean simulation) {
        this.gameStateNode = gameStateNode;
        this.game = (Empire) gameStateNode.getGame();
        this.units = game.getUnitsByPlayer(playerId);
        this.playerId = playerId;
        this.log = log;
        this.simulation = simulation;
    }



}
