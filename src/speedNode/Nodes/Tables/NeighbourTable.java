package speedNode.Nodes.Tables;

import speedNode.Utils.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class NeighbourTable implements INeighbourTable{
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();
    private final HashMap<String, Tuple<Boolean,Boolean>> Neighbours = new HashMap<>();


    @Override
    public boolean addNeighbour(String ip) {
        try{
            readWriteLock.writeLock().lock();
            if (this.Neighbours.containsKey(ip)) return false;
            else {
                this.Neighbours.put(ip, new Tuple<>(false, false));
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public boolean addNeighbours(List<String> ips) {
        try{
            readWriteLock.writeLock().lock();
            if (ips.stream().anyMatch((x) -> !this.Neighbours.containsKey(x))) return false;
            else{
                ips.stream().map((x)->this.Neighbours.put(x,new Tuple<>(false, false)));
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public List<String> getNeighbours() {
        try {
            readWriteLock.readLock().lock();
            ArrayList<String> ips = new ArrayList<>(); 
            this.Neighbours.keySet().stream().map((x)->ips.add(x));
            return ips;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean isActive(String ip) {
        try {
            readWriteLock.readLock().lock();
            if (!this.Neighbours.containsKey(ip)) return false;
            else return this.Neighbours.get(ip).fst;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean isConnected(String ip) {
        try {
            readWriteLock.readLock().lock();
            if (!this.Neighbours.containsKey(ip)) return false;
            else return this.Neighbours.get(ip).snd;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean updateConnectionNeighbour(String ip, boolean connected) {
        try {
            readWriteLock.writeLock().lock();
            if (!this.Neighbours.containsKey(ip)) return false;
            else {
                Boolean active = this.Neighbours.get(ip).fst;
                Tuple<Boolean,Boolean> tmp = new Tuple<>(active, connected);
                this.Neighbours.replace(ip,tmp);
                return true;
            }

        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public boolean updateActiveState(String ip, boolean activate) {
        try {
            readWriteLock.writeLock().lock();
            if (!this.Neighbours.containsKey(ip)) return false;
            else {
                Boolean connected = this.Neighbours.get(ip).snd;
                Tuple<Boolean,Boolean> tmp = new Tuple<>(activate,connected);
                this.Neighbours.replace(ip,tmp);
                return true;
            }

        }finally {
            readWriteLock.writeLock().unlock();
        }
    }
}
