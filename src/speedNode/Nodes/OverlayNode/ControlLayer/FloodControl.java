package speedNode.Nodes.OverlayNode.ControlLayer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class FloodControl {
    private final Map<String, Set<String>> floodSent = new HashMap<>(); //Neighbours that received the flood frame
    private final Map<String, Set<String>> floodReceived = new HashMap<>(); //Neighbours from which the flood frame was received
    private final Map<String,Integer> floodIndexes = new HashMap<>(); //Index of the last flood for each server
    private final ReentrantLock lock = new ReentrantLock();

    /**
     * @param server IP of the server that started the flood
     * @return current flood index for the given server
     */
    public Integer getCurrentFloodIndex(String server){
        try {
            lock.lock();
            return floodIndexes.get(server);
        }finally { lock.unlock(); }
    }

    /**
     * @param server IP of the server that started the flood
     * @return next flood index for the given server. If no index has been registered, then the value given is 0.
     */
    public int getNextFloodIndex(String server){
        try {
            lock.lock();
            Integer currentIndex = floodIndexes.get(server);
            if(currentIndex == null || currentIndex == Integer.MAX_VALUE)
                return 0;
            else
                return ++currentIndex;
        }finally { lock.unlock(); }
    }

    /**
     * If the flood index, of the frame received, is valid, registers that the node has sent the frame of the current flood
     * @param server Server that started the flood
     * @param node Node that sent the flood frame
     * @param index Received flood index of the server
     * @return "true" if the flood index of the frame received is valid. Otherwise, "false".
     */
    public boolean receivedFlood(String server, String node, int index){
        try {
            lock.lock();
            Integer currentIndex = updateFloodVars(server, index);

            //If the current index is not the same as the index given, the changes are rejected
            if(currentIndex != index)
                return false;

            var receivedSet = floodReceived.get(server);
            receivedSet.add(node);
            return true;
        }finally { lock.unlock(); }
    }

    /**
     * If the flood index is valid, registers that the node has received the frame of the current flood
     * @param server IP of the server that started the flood
     * @param node Node that received the flood frame
     * @param index Received flood index of the server
     * @return "true" if the flood index of the frame sent is valid. Otherwise, "false".
     */
    public boolean sentFlood(String server, String node, int index){
        try {
            lock.lock();
            Integer currentIndex = updateFloodVars(server, index);

            //If the current index is not the same as the index given, the changes are rejected, and the current flood index is returned
            if(currentIndex != index)
                return false;

            var sentSet = floodSent.get(server);
            sentSet.add(node);
            return true;
        }finally { lock.unlock(); }
    }

    /**
     * @param server IP of the server that started the flood
     * @return Set of nodes that either sent a flood frame, or received a flood frame.
     * If no nodes are found, returns empty set.
     */
    public Set<String> floodedNodes(String server){
        try {
            lock.lock();
            Set<String> set = new HashSet<>();

            var sentSet = floodSent.get(server);
            if (sentSet != null)
                set.addAll(sentSet);

            var receivedSet = floodReceived.get(server);
            if (receivedSet != null)
                set.addAll(receivedSet);

            return set;
        }finally { lock.unlock(); }
    }

    /**
     * Updates flood index of the given server, if the flood index, given as parameter, is superior to the one stored.
     * If the flood index is updated, the set variables associated with the server are cleared.
     * Assumes the caller has the lock.
     * @param server IP of the server that started the flood
     * @param floodIndex Received flood index of the server
     * @return current flood index
     */
    private Integer updateFloodVars(String server, int floodIndex) {
        var currentIndex = floodIndexes.get(server);

        //If a flood has not been received from the server given as parameter, then an index and the required sets are created
        if (currentIndex == null) {
            floodIndexes.put(server, floodIndex);
            floodReceived.put(server, new HashSet<>());
            floodSent.put(server, new HashSet<>());
            return floodIndex;
        }
        //If the new index is valid, then it is saved, and the sets associated to the server are cleared
        else if (currentIndex < floodIndex || (currentIndex == Integer.MAX_VALUE && floodIndex != currentIndex)) {
            floodIndexes.put(server, floodIndex);
            floodReceived.get(server).clear();
            floodSent.get(server).clear();
            return floodIndex;
        }

        return currentIndex;
    }
}
