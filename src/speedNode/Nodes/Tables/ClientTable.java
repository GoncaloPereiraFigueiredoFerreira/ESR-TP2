package speedNode.Nodes.Tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientTable implements IClientTable{
    private final HashMap<String,String> ClientTable = new HashMap<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    @Override
    public boolean addNewClient(String Ip) {
        try{
            readWriteLock.writeLock().lock();
            if (this.ClientTable.containsKey(Ip)) return false;
            else{
                this.ClientTable.put(Ip,"Client");
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public boolean addNewServer(String Ip) {
        try{
            readWriteLock.writeLock().lock();
            if (this.ClientTable.containsKey(Ip)) return false;
            else{
                this.ClientTable.put(Ip,"Server");
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public String typeOf(String Ip) {
        try{
            readWriteLock.readLock().lock();
            if (!this.ClientTable.containsKey(Ip)) return "";
            else{
                return this.ClientTable.get(Ip);
            }
        }finally {
            readWriteLock.readLock().unlock();
        }
    }


    @Override
    public List<String> getAllClients() {
        try {
            readWriteLock.readLock().lock();
            ArrayList<String> ips = new ArrayList<>();
            this.ClientTable.keySet().stream().filter(x-> Objects.equals(typeOf(x), "Client")).map((x)->ips.add(x));
            return ips;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public List<String> getAllServers() {
        try {
            readWriteLock.readLock().lock();
            ArrayList<String> ips = new ArrayList<>();
            this.ClientTable.keySet().stream().filter(x-> Objects.equals(typeOf(x), "Server")).map((x)->ips.add(x));
            return ips;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean removeClient(String IP) {
        try {
            readWriteLock.writeLock().lock();
            if (!this.ClientTable.containsKey(IP)) return false;
            else {
                this.ClientTable.remove(IP);
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }
}
