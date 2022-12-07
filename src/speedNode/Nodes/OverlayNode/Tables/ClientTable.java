package speedNode.Nodes.OverlayNode.Tables;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class ClientTable implements IClientTable{
    /**
     * Table that contains the columns:
     *                 IP address of client     |        Type of client (either "Server" or "Client")
     */
    private final HashMap<String,String> ClientTable = new HashMap<>();
    private final ReadWriteLock readWriteLock = new ReentrantReadWriteLock();


    public ClientTable(){}

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
            for (String i : this.ClientTable.keySet()){
                if (this.ClientTable.get(i).equals("Client")){
                    ips.add(i);
                }
            }
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
            for (String i : this.ClientTable.keySet()){

                if (this.ClientTable.get(i).equals("Server")){
                    ips.add(i);
                }
            }
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
