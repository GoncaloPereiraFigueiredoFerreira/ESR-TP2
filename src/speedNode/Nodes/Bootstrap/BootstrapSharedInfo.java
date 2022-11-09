package speedNode.Nodes.Bootstrap;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BootstrapSharedInfo {
    private Map<String, List<String>> nodesMap; //Overlay nodes and their respective neighbours
    private boolean serverClosed = false; //TODO - Modified by SIGINT, provavelmente nem se precisa desta variavel, ver https://stackoverflow.com/questions/2541597/how-to-gracefully-handle-the-sigkill-signal-in-java

    // TODO - Update implementation when possible
    // The first implementation requires all nodes to be alive to start the flood
    private final Set<String> contactedNodes = new HashSet<>();
    private boolean allContacted = false;
    private final ReentrantLock contactedNodesRL = new ReentrantLock();
    private final Condition allContactedCond = contactedNodesRL.newCondition();

    public BootstrapSharedInfo(Map<String, List<String>> nodesMap) {
        this.nodesMap = nodesMap;
    }

    public List<String> getNeighbours(String node){
        return new ArrayList<>(nodesMap.get(node));
    }

    public void addContactedNode(String contactedNode){
        contactedNodes.add(contactedNode);

        try {
            contactedNodesRL.lock();

            // Checks if every node of the overlay has contacted the bootstrap, meaning, that they all should be operating
            allContacted = contactedNodes.size() == nodesMap.size();

            // When all the nodes get contacted, signals the threads in charge of giving permission to the servers waiting to start flooding
            if(allContacted)
                allContactedCond.signalAll();
        }finally {
            contactedNodesRL.unlock();
        }
    }

    //If every node of the overlay has contacted the bootstrap, all the nodes should be operating, therefore all the servers can start the flood
    public void canStartFlood(){
        try{
            contactedNodesRL.lock();
            while (!allContacted){
                try { allContactedCond.await(); }
                catch (InterruptedException ignored){}
            }
        }finally {
            contactedNodesRL.unlock();
        }
    }

    public boolean isServerClosed() {
        return serverClosed;
    }

    public void setServerClosed(boolean serverClosed) {
        this.serverClosed = serverClosed;
    }
}
