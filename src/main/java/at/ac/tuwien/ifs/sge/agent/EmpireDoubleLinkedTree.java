package at.ac.tuwien.ifs.sge.agent;

import at.ac.tuwien.ifs.sge.core.util.tree.DoubleLinkedTree;
import at.ac.tuwien.ifs.sge.core.util.tree.Tree;
import at.ac.tuwien.ifs.sge.game.empire.communication.event.EmpireEvent;

public class EmpireDoubleLinkedTree extends DoubleLinkedTree<GameStateNode<EmpireEvent>> {
    public EmpireDoubleLinkedTree() {
    }

    public EmpireDoubleLinkedTree(GameStateNode<EmpireEvent> elem) {
        super(elem);
    }

    public EmpireDoubleLinkedTree(Tree<GameStateNode<EmpireEvent>> tree) {
        super(tree);
    }
}
