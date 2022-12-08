package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Utilities.Tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

public class ClientTable implements IClientTable{
    /**
     * Table that contains the columns:
     *                 IP address of client     |        Type of client (either "Server" or "Client")
     */

    private final HashSet<String> ClientTable = new HashSet<>();
    private final HashSet<String> ServerTable = new HashSet<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    public ClientTable(){}

    @Override
    public boolean addNewClient(String Ip) {
        try{
            readWriteLock.writeLock().lock();
            if (this.ClientTable.contains(Ip)) return false;
            else{
                this.ClientTable.add(Ip);
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
            if (this.ServerTable.contains(Ip))return false;
            else{
                this.ServerTable.add(Ip);
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
            return new ArrayList<>(this.ClientTable);

        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public List<String> getAllServers() {
        try {
            readWriteLock.readLock().lock();
            return new ArrayList<>(this.ServerTable);
        }finally {
            readWriteLock.readLock().unlock();
        }
    }

    @Override
    public boolean removeClient(String IP) {
        try {
            readWriteLock.writeLock().lock();
            if (!this.ClientTable.contains(IP)) return false;
            else {
                this.ClientTable.remove(IP);
                return true;
            }
        }finally {
            readWriteLock.writeLock().unlock();
        }
    }
}
