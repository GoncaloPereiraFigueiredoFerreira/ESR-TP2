package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Utilities.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NeighbourTableAntiga implements INeighbourTableAntiga {
    /**
     * Table that contains the columns:
     *
     *     IP of the neighbour    |   Flag: Is it connected   |   Flag: Does it want the stream  |     Timestamp of last jump
     *
     */
    private final HashMap<String, Tuple<Boolean,Boolean>> neighbours = new HashMap<>();
    private final HashMap<String, Long> timeStamp = new HashMap<>();
    private final ReadWriteLock neighboursLock = new ReentrantReadWriteLock();
    private final ReadWriteLock timestampLock = new ReentrantReadWriteLock();


    public NeighbourTableAntiga(){

    }

    @Override
    public boolean addNeighbour(String ip) {
        try{
            neighboursLock.writeLock().lock();
            timestampLock.writeLock().lock();
            if (this.neighbours.containsKey(ip)) return false;
            else {
                this.neighbours.put(ip, new Tuple<>(false, false));
                this.timeStamp.put(ip, (long) -1); // Inicialmente o timestamp estará a -1 se a conexão ainda não tiver sido usada
                return true;
            }
        }finally {
            neighboursLock.writeLock().unlock();
            timestampLock.writeLock().unlock();
        }
    }

    @Override
    public boolean addNeighbours(List<String> ips) {
        try{
            neighboursLock.writeLock().lock();
            if(ips != null){
                ips.forEach(ip -> {
                    if(!this.neighbours.containsKey(ip)){
                        this.neighbours.put(ip, new Tuple<>(false, false));
                    }
                });
                return true;
            }
            return false;
        }finally {
            neighboursLock.writeLock().unlock();
        }
    }

    @Override
    public List<String> getNeighbours() {
        try {
            neighboursLock.readLock().lock();
            return new ArrayList<>(this.neighbours.keySet());
        }finally {
            neighboursLock.readLock().unlock();
        }
    }

    @Override
    public boolean wantsStream(String ip) {
        try {
            neighboursLock.readLock().lock();
            if (!this.neighbours.containsKey(ip)) return false;
            else return this.neighbours.get(ip).snd;
        }finally {
            neighboursLock.readLock().unlock();
        }
    }

    @Override
    public boolean isConnected(String ip) {
        try {
            neighboursLock.readLock().lock();
            if (!this.neighbours.containsKey(ip)) return false;
            else return this.neighbours.get(ip).fst;
        }finally {
            neighboursLock.readLock().unlock();
        }
    }

    @Override
    public boolean updateConnectionNeighbour(String ip, boolean connected) {
        try {
            neighboursLock.writeLock().lock();
            if (!this.neighbours.containsKey(ip)) return false;
            else {
                Boolean active = this.neighbours.get(ip).fst;
                Tuple<Boolean,Boolean> tmp = new Tuple<>(active, connected);
                this.neighbours.replace(ip,tmp);
                return true;
            }

        }finally {
            neighboursLock.writeLock().unlock();
        }
    }

    @Override
    public boolean updateWantsStream(String ip, boolean activate) {
        try {
            neighboursLock.writeLock().lock();
            if (!this.neighbours.containsKey(ip)) return false;
            else {
                Boolean connected = this.neighbours.get(ip).snd;
                Tuple<Boolean,Boolean> tmp = new Tuple<>(activate,connected);
                this.neighbours.replace(ip,tmp);
                return true;
            }

        }finally {
            neighboursLock.writeLock().unlock();
        }
    }

    @Override
    public List<String> getNeighboursWantingStream(){
        ArrayList<String> lst = new ArrayList<>();
        try {
            neighboursLock.readLock().lock();
        for (Map.Entry<String,Tuple<Boolean,Boolean>> entry: this.neighbours.entrySet() ){
            if (entry.getValue().snd == Boolean.TRUE) lst.add(entry.getKey());
        }
        return lst;
        }finally {
            neighboursLock.readLock().unlock();
        }
    }

    public long getLastJumpTime(String ip){
        try{
            timestampLock.readLock().lock();
            return timeStamp.get(ip);
        }finally {
            timestampLock.readLock().unlock();
        }
    }

    public void updateLastJumpTime(String ip,long newTimeStamp){
        try{
            timestampLock.writeLock().lock();
            timeStamp.replace(ip,newTimeStamp);
        }finally {
            timestampLock.writeLock().unlock();
        }
    }









}
