package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.engine.logging.Logger;

public class MacroActionFactory<A> {


    public MacroActionFactory() {
    }

    public MacroAction<A> createMacroAction(MacroActionType type, GameStateNode<A> gameStateNode, int playerId, Logger log, boolean simulate) {
        return switch (type) {
            case EXPLORATION -> new ExplorationMacroAction<>(gameStateNode, playerId, log, simulate);
            case EXPANSION -> new ExpansionMacroAction<>(gameStateNode, playerId, log, simulate);
            case ATTACK -> new AttackMacroAction<>(gameStateNode,playerId,log,simulate);
            // Add other cases here for other types of MacroActions
            default -> throw new IllegalArgumentException("Invalid MacroActionType: " + type);
        };
    }
}

