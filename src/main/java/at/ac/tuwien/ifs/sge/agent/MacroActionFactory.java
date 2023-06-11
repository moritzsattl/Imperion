package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;

public class MacroActionFactory<A> {


    public MacroActionFactory() {
    }

    public MacroAction<A> createMacroAction(MacroActionType type, GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulate) {
        switch (type) {
            case EXPLORATION:
                return new ExplorationMacroAction<A>(gameStateNode, playerId,log, simulate);
            // Add other cases here for other types of MacroActions
            default:
                throw new IllegalArgumentException("Invalid MacroActionType: " + type);
        }
    }
}

