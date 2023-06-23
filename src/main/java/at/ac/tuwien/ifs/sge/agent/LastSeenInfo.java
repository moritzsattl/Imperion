package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.map.Position;

public record LastSeenInfo(Position position, long timeStamp) { }
