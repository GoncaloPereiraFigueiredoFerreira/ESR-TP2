package speedNode.Nodes.Bootstrap;

import speedNode.Utilities.Tuple;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class BootstrapSharedInfo {
    // Key : node name   |   Value :  ( Set of node's interfaces, Set of node's neighbours )
    private final Map<String, Tuple<Set<String>,Set<String>>> nodesMap; //Overlay nodes and their respective neighbours
    private boolean serverClosed = false; //TODO - Modified by SIGINT, provavelmente nem se precisa desta variavel, ver https://stackoverflow.com/questions/2541597/how-to-gracefully-handle-the-sigkill-signal-in-java
    private final Set<String> contactedNodes = new HashSet<>();
    private final ReentrantLock contactedNodesRL = new ReentrantLock();
    //private final Condition allContactedCond = contactedNodesRL.newCondition();
    private final Condition readyToStartCond = contactedNodesRL.newCondition();
    private final int minOfReadyNodesToStart = 4;

    public BootstrapSharedInfo(Map<String, Tuple<Set<String>,Set<String>>> nodesMap) {
        this.nodesMap = nodesMap;
    }


    public String getNodeNameByInterface(String interfaceIP){
        for(var node : nodesMap.entrySet())
            if(node.getValue().fst.contains(interfaceIP))
                return node.getKey();
        return null;
    }

    /**
     * @param interfaceIP IP of the interface used to contact the bootstrap
     * @return list containing the name of node (first element), followed by the
     * neighbours info ( name of neighbour, the local interface that should be used to
     * contact the neighbour, and the neighbours interface).
     * Eg.: [nodeName, neighbour1_name,local_interface_to_contact_neighbour1,
     * neighbour1_interface, ... , neighbourN_name,local_interface_to_contact_neighbourN,
     * neighbourN_interface]
     */
    public List<String> getNameAndNeighbours(String interfaceIP){
        String nodeName = getNodeNameByInterface(interfaceIP);

        var neighbours = nodesMap.get(nodeName);
        if(neighbours == null)
            return null;

        Set<String> nodeInterfaces = neighbours.fst;
        List<String> ret = new ArrayList<>();
        ret.add(nodeName);

        for(String neighbourName : neighbours.snd){
            Tuple<String, String> interfacesPair = chooseInterfaces(nodeInterfaces, getNodeInterfaces(neighbourName));
            ret.add(neighbourName);
            ret.add(interfacesPair.fst);
            ret.add(interfacesPair.snd);
        }

        return ret;
    }

    /**
     * @param node Name of the node
     * @return all interfaces associated with the node (registered in the config file)
     */
    public Set<String> getNodeInterfaces(String node){
        var neighbourTuple = nodesMap.get(node);
        if(neighbourTuple == null) return null;
        return new HashSet<>(neighbourTuple.fst);
    }

    /**
     * Tries to match interfaces belonging to the same network (Assumes a mask of 255.255.255.0).
     * @param n1_interfaces Set of every interface of node 1
     * @param n2_interfaces Set of every interface of node 2
     * @return (interface of the node 1, interface of the node 2)
     */
    private Tuple<String,String> chooseInterfaces(Set<String> n1_interfaces, Set<String> n2_interfaces){
        for (String n1_int : n1_interfaces){
            for (String n2_int : n2_interfaces){
                try {
                    byte[] tempN1IP = Arrays.copyOfRange( InetAddress.getByName(n1_int).getAddress(),0,3);
                    byte[] tempN2IP = Arrays.copyOfRange( InetAddress.getByName(n2_int).getAddress(),0,3);
                    if (Arrays.equals(tempN1IP,tempN2IP)) {
                        return new Tuple<>(n1_int, n2_int);
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }
        return new Tuple<>(n1_interfaces.iterator().next(), n2_interfaces.iterator().next());
    }

    public void addContactedNode(String contactedNode){
        contactedNodes.add(contactedNode);
        try {
            contactedNodesRL.lock();
            if(readyToStart())
                readyToStartCond.signalAll();
        }finally { contactedNodesRL.unlock(); }
    }

    //Assumes that the caller holds the lock
    private boolean readyToStart(){
        return contactedNodes.size() >= minOfReadyNodesToStart;
    }

    public void waitForStartConditions() throws InterruptedException {
        try{
            contactedNodesRL.lock();
            while (!readyToStart())
                readyToStartCond.await();
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
