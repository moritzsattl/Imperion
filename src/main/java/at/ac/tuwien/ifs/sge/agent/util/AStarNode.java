package at.ac.tuwien.ifs.sge.agent.util;

import at.ac.tuwien.ifs.sge.game.empire.map.Position;

import java.util.Objects;

public class AStarNode implements Comparable<AStarNode> {

    private final Position position;

    private int tfCost,thCost = 0;

    private AStarNode prev;

    public AStarNode(Position position) {
        this.position = position;
    }

    public void setPreviousNode(AStarNode currentNode) {
        prev = currentNode;
    }

    public int getX() {
        return position.getX();
    }

    public int getY() {
        return position.getY();
    }

    public int getThCost() {
        return thCost;
    }

    public int getTfCost() {
        return tfCost;
    }

    public void sethCosts(int estimatedDistance) {
        thCost = estimatedDistance;
    }

    public void setfCosts(int i) {
        tfCost = i;
    }

    public Position getPosition() {
        return position;
    }

    public AStarNode getPrev() {
        return prev;
    }

    @Override
    public String toString() {
        return "AStarNode{" +
                "position=" + position +
                '}';
    }

    @Override
    public int compareTo(AStarNode otherNode) {
        if (this.getTfCost() < otherNode.getTfCost()) {
            return -1;
        } else if (this.getTfCost() > otherNode.getTfCost()) {
            return 1;
        } else {
            // If the fCost is equal, compare based on hCost (tie-breaker)
            return Integer.compare(this.getThCost(), otherNode.getThCost());
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AStarNode that = (AStarNode) o;
        return position.equals(that.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(position);
    }


}