package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Nodes.OverlayNode.ControlLayer.ConnectionHandler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class NeighbourTable implements INeighbourTable{
    /*
     * Table that contains the columns:
     *
     *     -> IP of the neighbour
     *     -> Connection Handler (Can determine if the neighbour is connected)
     *     -> Flag: Does it want the stream
     *     -> Timestamp of last jump
     *
     */

    private final HashMap<String, NeighbourEntry> neighbours = new HashMap<>();
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();


    public NeighbourTable(){}

    public boolean addNeighbour(String ip) {
        if(ip == null)
            return false;

        try{
            rwlock.writeLock().lock();

            if(neighbours.containsKey(ip))
                return false;

            neighbours.put(ip, new NeighbourEntry(ip, null, false, -1L)); // Inicialmente o timestamp estará a -1 se a conexão ainda não tiver sido usada
        }finally {
            rwlock.writeLock().unlock();
        }

        return true;
    }

    public boolean addNeighbours(List<String> ips) {
        try{
            rwlock.writeLock().lock();
            if(ips != null){
                ips.forEach(this::addNeighbour);
                return true;
            }
            return false;
        }finally {
            rwlock.writeLock().unlock();
        }
    }

    public List<String> getNeighbours() {
        try {
            rwlock.readLock().lock();
            return new ArrayList<>(this.neighbours.keySet());
        }finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public List<String> getConnectedNeighbours() {
        try {
            rwlock.readLock().lock();
            return this.neighbours.values().stream()
                                           .filter(NeighbourEntry::isConnected)
                                           .map(NeighbourEntry::getIp)
                                           .collect(Collectors.toList());
        } finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public List<String> getUnconnectedNeighbours() {
        try {
            rwlock.readLock().lock();
            return this.neighbours.values().stream()
                    .filter(ne -> !ne.isConnected())
                    .map(NeighbourEntry::getIp)
                    .collect(Collectors.toList());
        } finally {
            rwlock.readLock().unlock();
        }
    }

    public boolean isConnected(String ip) {
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighbours.get(ip);
            if (ne != null) return ne.isConnected();
            return false;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    @Override
    public ConnectionHandler getConnectionHandler(String ip) {
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighbours.get(ip);
            if (ne != null) return ne.getConnectionHandler();
            return null;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public boolean wantsStream(String ip) {
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighbours.get(ip);
            if (ne != null) return ne.getWantsStream();
            return false;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public ConnectionHandler updateConnectionHandler(String ip, ConnectionHandler ch) {
        try {
            rwlock.writeLock().lock();
            NeighbourEntry ne = neighbours.get(ip);
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
            NeighbourEntry ne = neighbours.get(ip);
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
            return neighbours.values().stream()
                                      .filter(NeighbourEntry::getWantsStream)
                                      .map(NeighbourEntry::getIp)
                                      .collect(Collectors.toList());
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public long getLastJumpTime(String ip){
        try {
            rwlock.readLock().lock();
            NeighbourEntry ne = neighbours.get(ip);
            return ne != null ? ne.getTimestamp() : -1;
        }finally {
            rwlock.readLock().unlock();
        }
    }

    public void updateLastJumpTime(String ip,long newTimeStamp){
        try {
            rwlock.writeLock().lock();
            NeighbourEntry ne = neighbours.get(ip);
            if (ne != null)
                ne.setTimestamp(newTimeStamp);
        }finally {
            rwlock.writeLock().unlock();
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
}
