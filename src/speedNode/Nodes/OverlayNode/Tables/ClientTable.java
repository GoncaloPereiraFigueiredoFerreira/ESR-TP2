package speedNode.Nodes.OverlayNode.Tables;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientTable implements IClientTable{
    /**
     * Table that contains the columns:
     *                 IP address of client     |        Type of client (either "Server" or "Client")
     */

    private final HashSet<String> clientTable = new HashSet<>();
    private final HashSet<String> serverTable = new HashSet<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    public ClientTable(){}

    @Override
    public boolean addNewClient(String Ip) {
        try{
            readWriteLock.writeLock().lock();
            if (this.clientTable.contains(Ip)) return false;
            else{
                this.clientTable.add(Ip);
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
            if (this.serverTable.contains(Ip))return false;
            else{
                this.serverTable.add(Ip);
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }


    @Override
    public List<String> getAllClients() {
        try {
            readWriteLock.readLock().lock();
            return new ArrayList<>(this.clientTable);

        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public List<String> getAllServers() {
        try {
            readWriteLock.readLock().lock();
            return new ArrayList<>(this.serverTable);
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean removeClient(String IP) {
        try {
            readWriteLock.writeLock().lock();
            if (!this.clientTable.contains(IP)) return false;
            else {
                this.clientTable.remove(IP);
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }

    @Override
    public boolean containsServer(String IP) {
        try {
            readWriteLock.readLock().lock();
            return serverTable.contains(IP);
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    public boolean hasClients(){
        try {
            readWriteLock.readLock().lock();
            return clientTable.size() != 0;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    public boolean hasServers(){
        try {
            readWriteLock.readLock().lock();
            return serverTable.size() != 0;
        }finally {
            readWriteLock.readLock().unlock();
        }
    }
}
