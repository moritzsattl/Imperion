package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.game.empire.map.Position;

import java.util.Objects;

class AStarNode implements Comparable<AStarNode> {

    private Position position;

    private int tfCost,thCost = 0;

    private AStarNode next, prev;

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

    public int gethCosts() {
        return thCost;
    }

    public int getfCost() {
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

    public AStarNode getNext() {
        return next;
    }

    @Override
    public String toString() {
        return "AStarNode{" +
                "position=" + position +
                ", next=" + next +
                '}';
    }

    @Override
    public int compareTo(AStarNode otherNode) {
        if (this.getfCost() < otherNode.getfCost()) {
            return -1;
        } else if (this.getfCost() > otherNode.getfCost()) {
            return 1;
        } else {
            // If the fCost is equal, compare based on hCost (tie-breaker)
            if (this.gethCosts() < otherNode.gethCosts()) {
                return -1;
            } else if (this.gethCosts() > otherNode.gethCosts()) {
                return 1;
            } else {
                return 0;
            }
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
