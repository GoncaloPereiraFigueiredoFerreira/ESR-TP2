package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Nodes.OverlayNode.ControlLayer.ConnectionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class NeighbourTable implements INeighbourTable{
    /*
     * Table that contains the columns:
     *     -> Name of the neighbour
     *     -> IP of the local interface that should be used to contact the neighbour
     *     -> IP of the neighbour's interface
     *     -> Connection Handler (Can determine if the neighbour is connected)
     *     -> Flag: Does it want the stream
     *     -> Timestamp of last jump
     *
     */

    private final Map<String, NeighbourEntry> neighboursInfo = new HashMap<>();
    private final Map<String, String> mapIpToName = new HashMap<>(); //Key : Neighbour's IP  | Value: Neighbour's name
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();


    public NeighbourTable(){}

    public boolean addNeighbour(String neighbourName, String localIP, String neighbourIP) {
        try{
            rwlock.writeLock().lock();

            if(neighboursInfo.containsKey(neighbourName))
                return false;

            neighboursInfo.put(neighbourName, new NeighbourEntry(neighbourName, null, false, -1L, localIP, neighbourIP)); // Inicialmente o timestamp estará a -1 se a conexão ainda não tiver sido usada
            mapIpToName.put(neighbourIP, neighbourName);
        }finally {
            rwlock.writeLock().unlock();
        }

        return true;
    }

    public List<String> getNeighbours() {
        try {
            rwlock.readLock().lock();
            return new ArrayList<>(this.neighboursInfo.keySet());
        }finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public List<String> getConnectedNeighbours() {
        try {
            rwlock.readLock().lock();
            return this.neighboursInfo.values().stream()
                                           .filter(NeighbourEntry::isConnected)
                                           .map(NeighbourEntry::getName)
                                           .collect(Collectors.toList());
        } finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public List<String> getUnconnectedNeighbours() {
        try {
            rwlock.readLock().lock();
            return this.neighboursInfo.values().stream()
                    .filter(ne -> !ne.isConnected())
                    .map(NeighbourEntry::getName)
                    .collect(Collectors.toList());
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public boolean isConnected(String ip) {
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighboursInfo.get(ip);
            if (ne != null) return ne.isConnected();
            return false;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public ConnectionHandler getConnectionHandler(String neighbourName) {
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighboursInfo.get(neighbourName);
            if (ne != null) return ne.getConnectionHandler();
            return null;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public boolean wantsStream(String ip) {
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighboursInfo.get(ip);
            if (ne != null) return ne.getWantsStream();
            return false;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public ConnectionHandler updateConnectionHandler(String ip, ConnectionHandler ch) {
        try {
            rwlock.writeLock().lock();
            NeighbourEntry ne = neighboursInfo.get(ip);
            ConnectionHandler previousCh = null;
            if (ne != null) {
                previousCh = ne.getConnectionHandler();
                ne.setConnectionHandler(ch);
            }
            return previousCh;
        }finally {
            rwlock.writeLock().unlock();
        }
    }

    public boolean updateWantsStream(String ip, boolean wants) {
        try {
            rwlock.writeLock().lock();
            NeighbourEntry ne = neighboursInfo.get(ip);
            if (ne != null) {
                ne.setWantsStream(wants);
                return true;
            }
            return false;
        }finally {
            rwlock.writeLock().unlock();
        }
    }

    public List<String> getNeighboursWantingStream(){
        try {
            rwlock.readLock().lock();
            return neighboursInfo.values().stream()
                                      .filter(NeighbourEntry::getWantsStream)
                                      .map(NeighbourEntry::getName)
                                      .collect(Collectors.toList());
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public long getLastJumpTime(String ip){
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighboursInfo.get(ip);
            return ne != null ? ne.getTimestamp() : -1;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public void updateLastJumpTime(String neighbourName, long newTimeStamp){
        try {
            rwlock.writeLock().lock();
            NeighbourEntry ne = neighboursInfo.get(neighbourName);
            if (ne != null)
                ne.setTimestamp(newTimeStamp);
        }finally {
            rwlock.writeLock().unlock();
        }
    }

    public String getNeighbourIP(String neighbourName){
        try {
            rwlock.readLock().lock();
            return this.neighboursInfo.get(neighbourName).getNeighbourIP();
        }catch (Exception e){
            return null;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public String anyNeighbourUsesInterfaceIP(String neighbourIP){
        try {
            rwlock.readLock().lock();
            for (NeighbourEntry n : this.neighboursInfo.values())
                if(n.getNeighbourIP().equals(neighbourIP))
                    return n.getName();
            return null;
        }finally {
            rwlock.readLock().unlock();
        }
    }


    public void setNeighbourIP(String neighbourName, String interfaceIp){
        try {
            rwlock.writeLock().lock();
            this.neighboursInfo.get(neighbourName).setNeighbourIP(interfaceIp);
        }catch (Exception ignored){
        }finally {
            rwlock.writeLock().unlock();
        }
    }

    @Override
    public String getLocalIP(String neighbourName) {
        try {
            rwlock.readLock().lock();
            return this.neighboursInfo.get(neighbourName).getLocalIP();
        }catch (Exception e){
            return null;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public void setLocalIP(String neighbourName, String interfaceIp) {
        try {
            rwlock.writeLock().lock();
            this.neighboursInfo.get(neighbourName).setLocalIP(interfaceIp);
        }catch (Exception ignored){
        }finally {
            rwlock.writeLock().unlock();
        }
    }

    @Override
    public String getNeighbourNameByItsIP(String neighbourIP) {
        try {
            rwlock.readLock().lock();
            return mapIpToName.get(neighbourIP);
        } finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public void writeLock(){
        rwlock.writeLock().lock();
    }

    @Override
    public void writeUnlock(){
        rwlock.writeLock().unlock();
    }

    @Override
    public void printTable() {
        try{
            rwlock.readLock().lock();
            System.out.println("\nneighboursTable={");
            for(var entry : neighboursInfo.values()){
                System.out.println(entry);
            }
            System.out.println("}\n");
        }finally {
            rwlock.readLock().unlock();
        }
    }
}
