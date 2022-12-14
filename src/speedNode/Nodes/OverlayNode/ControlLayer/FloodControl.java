package speedNode.Nodes.OverlayNode.ControlLayer;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
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
                return currentIndex + 1;
        }finally { lock.unlock(); }
    }

    /**
     * If the flood index, of the frame received, is valid, registers that the node has sent the frame of the current flood
     * @param server Server that started the flood
     * @param index Received flood index of the server
     * @return "true" if the flood index of the frame received is valid. Otherwise, "false".
     */
    public boolean validateFlood(String server, int index){
        try {
            lock.lock();
            Integer currentIndex = updateFloodVars(server, index);
            //If the current index is not the same as the index given, the changes are rejected
            return currentIndex == index;
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


    public static class FloodInfo{
        public String server;
        public int jumps;
        public long timestamp;
        public List<String> route;

        public FloodInfo(String server, Integer jumps, Long timestamp, List<String> route) {
            this.server = server;
            this.jumps = jumps;
            this.timestamp = timestamp;
            this.route = route;
        }

        public byte[] serialize() throws IOException {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectOutputStream out = new ObjectOutputStream(baos);

            //Serialize server identification
            out.write(InetAddress.getByName(server).getAddress());

            //Serialize nr of jumps and timestamp
            out.writeInt(jumps);
            out.writeLong(timestamp);

            //Serialize route
            out.writeInt(route.size()); //writes number of nodes belonging to the route
            for(String routeNode : route)
                out.writeUTF(routeNode);

            out.flush();
            byte[] byteArray = baos.toByteArray();
            out.close();
            baos.close();

            return byteArray;
        }

        public static FloodInfo deserialize(byte[] data) throws IOException {
            ByteArrayInputStream bais = new ByteArrayInputStream(data);
            ObjectInputStream ois = new ObjectInputStream(bais);

            String server = InetAddress.getByAddress(ois.readNBytes(4)).getHostAddress();
            int jumps = ois.readInt();
            long timestamp = ois.readLong();

            List<String> route = new ArrayList<>();
            int nrOfNodes = ois.readInt();
            for(int i = 0 ; i < nrOfNodes ; i++)
                route.add(ois.readUTF());

            ois.close();
            bais.close();
            return new FloodInfo(server, jumps, timestamp, route);
        }
    }
}
