package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.agent.Agent;
import at.ac.tuwien.ifs.sge.core.agent.GameAgent;
import at.ac.tuwien.ifs.sge.core.game.Game;
import at.ac.tuwien.ifs.sge.core.game.RealTimeGame;
import at.ac.tuwien.ifs.sge.core.game.exception.ActionException;
import at.ac.tuwien.ifs.sge.game.empire.core.Empire;

import java.util.List;
import java.util.Stack;

public interface MacroAction<A>{

    List<?> getResponsibleActions();

    void simulate() throws ActionException;
}
