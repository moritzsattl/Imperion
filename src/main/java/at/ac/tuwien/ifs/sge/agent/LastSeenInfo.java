package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.map.Position;

public class LastSeenInfo {
    private Position position;
    private long timeStamp;

    public LastSeenInfo(Position position, long timeStamp) {
        this.position = position;
        this.timeStamp = timeStamp;
    }

    public Position getPosition() {
        return position;
    }

    public long getTimeStamp() {
        return timeStamp;
    }
}
