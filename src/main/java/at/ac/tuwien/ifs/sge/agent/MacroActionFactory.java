package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;

public class MacroActionFactory<A> {


    public MacroActionFactory() {
    }

    public MacroAction<A> createMacroAction(MacroActionType type, GameStateNode<A> gameStateNode, int playerId, Logger log) {
        switch (type) {
            case MOVE_UNITS:
                return new MoveMacroAction<A>(gameStateNode, playerId,log);
            // Add other cases here for other types of MacroActions
            default:
                throw new IllegalArgumentException("Invalid MacroActionType: " + type);
        }
    }
}

