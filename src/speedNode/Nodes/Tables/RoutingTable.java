package speedNode.Nodes.Tables;

import speedNode.Utilities.Tuple;

import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RoutingTable implements IRoutingTable{
    /**
     * Table that contains the columns:
     *
     *     IP of the server | IP of the Providing Neighbour | Nº Jumps till server |   Time till Server   |  Flag : Is route active
     *
     */
    private final HashMap<Tuple<String,String>, Tuple<Integer,Float>> metricsTable = new HashMap<>();
    private final HashMap<Tuple<String,String>, Boolean> activeRoute = new HashMap<>();
    private final HashMap<String, List<String>> providers = new HashMap<>();

    private final ReadWriteLock readWriteLockMetrics = new ReentrantReadWriteLock();
    private final ReadWriteLock readWriteLockActive = new ReentrantReadWriteLock();
    private final ReadWriteLock readWriteLockProviders = new ReentrantReadWriteLock();

    public RoutingTable(){

    }

    @Override
    public boolean addServerPath(String ServerIp, String Provider, int Jumps, float Time, boolean active) {
        Tuple<String, String> temp = new Tuple<>(Provider, ServerIp);
        try {
            readWriteLockProviders.writeLock().lock();
            if (this.providers.containsKey(Provider) && this.providers.get(Provider).contains(ServerIp)) return false;
            else{
                try {
                    readWriteLockMetrics.writeLock().lock();
                    readWriteLockActive.writeLock().lock();

                    if (this.providers.containsKey(Provider))
                        this.providers.get(Provider).add(ServerIp);
                    else this.providers.put(Provider,new ArrayList<>(Arrays.asList(ServerIp)));

                    this.metricsTable.put(temp,new Tuple<>(Jumps,Time));
                    this.activeRoute.put(temp,active);
                    return true;

                }finally {
                    readWriteLockActive.writeLock().unlock();
                    readWriteLockMetrics.writeLock().unlock();
                }
            }
        }finally {
            readWriteLockProviders.writeLock().unlock();
        }
    }

    @Override
    public boolean updateMetrics(String ServerIp, String Provider, int Jumps, float Time) {
        Tuple<String, String> temp = new Tuple<>(Provider, ServerIp);
        try {
            readWriteLockMetrics.writeLock().lock();
            if (!this.metricsTable.containsKey(temp)) return false;
            else{
                Tuple<Integer,Float> temp2 = new Tuple<>(Jumps,Time);
                this.metricsTable.replace(temp,temp2);
                return true;
            }

        }finally {
            readWriteLockMetrics.writeLock().unlock();
        }
    }

    @Override
    public boolean activateRoute(String ServerIp, String Provider) {
        Tuple<String, String> temp = new Tuple<>(Provider, ServerIp);
        try {
            readWriteLockActive.writeLock().lock();
            if (!this.activeRoute.containsKey(temp)) return false;
            else{
                Tuple<String,String> activeRoute = this.getActiveRoute();
                this.activeRoute.replace(activeRoute,false);
                this.activeRoute.replace(temp,true);
                return true;
            }

        }finally {
            readWriteLockActive.writeLock().unlock();
        }
    }

    @Override
    public boolean isRouteActive(String ServerIp, String Provider) {
        try{
            this.readWriteLockActive.readLock().lock();
            Tuple<String,String> t = new Tuple<>(Provider,ServerIp);
            if (!this.activeRoute.containsKey(t)) return false;
            else {
                return this.activeRoute.get(t);
            }
        }finally {
            this.readWriteLockActive.readLock().unlock();
        }
    }

    @Override
    public Tuple<Integer,Float> getMetrics(String ServerIp, String Provider) {
        try{
            this.readWriteLockMetrics.readLock().lock();
            Tuple<String,String> t = new Tuple<>(Provider,ServerIp);
            if (!this.metricsTable.containsKey(t)) return null;
            else {
                return this.metricsTable.get(t).clone();
            }
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }

    public Tuple<String,String> getActiveRoute(){
        try{
            this.readWriteLockActive.readLock().unlock();
            Tuple<String,String> bestRoute = null;
            for (Map.Entry<Tuple<String,String>,Boolean> entry : this.activeRoute.entrySet()) {
                if (entry.getValue()) bestRoute = entry.getKey().clone();
            }
            return bestRoute;
        }finally {
            this.readWriteLockActive.readLock().unlock();
        }
    }

    @Override
    public boolean activateBestRoute() {
        try{
            this.readWriteLockMetrics.readLock().lock();
            float wiggleRoom = 0.05f;
            float score;
            float minScore = Float.MAX_VALUE;
            Tuple<String,String> bestRoute =null;

            for (Map.Entry<Tuple<String,String>,Tuple<Integer,Float>> entry : this.metricsTable.entrySet()){
                score = entry.getValue().snd + (entry.getValue().fst * wiggleRoom);
                if (score < minScore) {
                    minScore= score;
                    bestRoute = entry.getKey();
                }
            }

            assert bestRoute != null;
            return activateRoute(bestRoute.fst,bestRoute.snd);
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }


    @Override
    public boolean existsInRoutingTable(String ServerIp, String Provider) {
        try{
            this.readWriteLockMetrics.readLock().lock();
            Tuple<String,String> t = new Tuple<>(Provider,ServerIp);
            return this.metricsTable.containsKey(t);
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }
}
