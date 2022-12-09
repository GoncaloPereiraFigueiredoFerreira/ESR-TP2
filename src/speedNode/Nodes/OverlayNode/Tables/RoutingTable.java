package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Utilities.BoolWithLockCond;
import speedNode.Utilities.Tuple;

import java.sql.Time;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class RoutingTable implements IRoutingTable{
    /**
     * Table that contains the columns:
     *
     *    IP of the server  | IP of the Providing Neighbour  |    Nº Jumps till server  |  Time till Server  |  Flag : Is route active
     *
     */
    private final HashMap<Tuple<String,String>, Tuple<Integer,Float>> metricsTable = new HashMap<>();
    private final HashMap<Tuple<String,String>, Boolean> activeRoute = new HashMap<>();

    /**
     *  IP of the providing neighbour | List of the servers that can be accessed by the neighbour
     */
    private final HashMap<String, List<String>> providers = new HashMap<>();

    private final ReadWriteLock readWriteLockMetrics = new ReentrantReadWriteLock();
    private final ReadWriteLock readWriteLockActive = new ReentrantReadWriteLock();
    private final ReadWriteLock readWriteLockProviders = new ReentrantReadWriteLock();

    private boolean delay = false;
    private final ReentrantLock reentrantLock = new ReentrantLock();

    public RoutingTable(){

    }

    @Override
    public boolean addServerPath(String ServerIp, String Provider, int Jumps, float Time, boolean active) {
        Tuple<String, String> temp = new Tuple<>(ServerIp, Provider);
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
        Tuple<String, String> temp = new Tuple<>(ServerIp, Provider);
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
        Tuple<String, String> temp = new Tuple<>(ServerIp, Provider);
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


    public void deactivateRoute(){
        try{
            readWriteLockActive.writeLock().lock();
            Tuple<String,String> activeRoute= this.getActiveRoute();
            this.activeRoute.replace(activeRoute,false);
        }finally {
            readWriteLockActive.writeLock().unlock();
        }
    }


    public void deactivateRoute(String provider){
        try{
            readWriteLockActive.writeLock().lock();
            Tuple<String,String> activeRoute;
            for (var route :this.activeRoute.keySet()){
                if (route.snd.equals(provider))
                    this.activeRoute.replace(route,false);
            }
        }finally {
            readWriteLockActive.writeLock().unlock();
        }
    }


    @Override
    public boolean isRouteActive(String ServerIp, String Provider) {
        try{
            this.readWriteLockActive.readLock().lock();
            Tuple<String,String> t = new Tuple<>(ServerIp,Provider);
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
            Tuple<String,String> t = new Tuple<>(ServerIp,Provider);
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
            this.readWriteLockActive.readLock().lock();
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
    public Tuple<String, String> activateBestRoute() {
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

            if(bestRoute != null && activateRoute(bestRoute.fst,bestRoute.snd))
                return bestRoute;
            else
                return null;
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }



    public Tuple<String, String> activateBestRoute(Set<String> excluded) {
        try{
            this.readWriteLockMetrics.readLock().lock();
            float wiggleRoom = 0.05f;
            float score;
            float minScore = Float.MAX_VALUE;
            Tuple<String,String> bestRoute =null;


            for (Map.Entry<Tuple<String,String>,Tuple<Integer,Float>> entry : this.metricsTable.entrySet()){
                if (!excluded.contains(entry.getKey().snd) ) { // if neighbour isn't excluded from being the best route
                    score = entry.getValue().snd + (entry.getValue().fst * wiggleRoom);
                    if (score < minScore) {
                        minScore = score;
                        bestRoute = entry.getKey();
                    }
                }
            }

            if(bestRoute != null && activateRoute(bestRoute.fst,bestRoute.snd))
                return bestRoute;
            else
                return null;
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }


    @Override
    public boolean existsInRoutingTable(String ServerIp, String Provider) {
        try{
            this.readWriteLockMetrics.readLock().lock();
            Tuple<String,String> t = new Tuple<>(ServerIp,Provider);
            return this.metricsTable.containsKey(t);
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }

    @Override
    public void printTables() {
        try{
            this.readWriteLockProviders.writeLock().lock();
            this.readWriteLockMetrics.writeLock().lock();
            this.readWriteLockActive.writeLock().lock();

            System.out.println("\n\n********** Routing tables **********\n");

            //print providers
            System.out.println("providers={");
            for(var providerEntry : providers.entrySet())
                System.out.println("\tprovider(neighbour): " + providerEntry.getKey() + " | servers: " + providerEntry.getValue());
            System.out.println("}\n");

            //print metrics
            System.out.println("routes={");
            for(var metricsEntry : metricsTable.entrySet()) {
                var tupleNodes = metricsEntry.getKey();
                var tupleMetrics = metricsEntry.getValue();
                System.out.println("\tserver: " + tupleNodes.fst + " | neighbour: " + tupleNodes.snd + " | jumps: " + tupleMetrics.fst + " | time: " + tupleMetrics.snd + " | active: " + activeRoute.get(tupleNodes));
            }
            System.out.println("}\n");

            System.out.println("\n**********************************\n\n");
        }finally {
            this.readWriteLockProviders.writeLock().unlock();
            this.readWriteLockMetrics.writeLock().unlock();
            this.readWriteLockActive.writeLock().unlock();
        }
    }

    @Override
    public boolean verifyDelay(String serverIP, String Provider,int jumps ,long newTime) {
        // Fazer conta para detetar delay
        // se foi detetado signalAll
        try{
            this.readWriteLockMetrics.writeLock().lock();
            reentrantLock.lock();

            var metrics = this.metricsTable.get(new Tuple<>(serverIP,Provider));
            if (newTime - metrics.snd > 0.05 * metrics.snd){
                System.out.println("ROUTING TABLE: DELAY DETETADO");
                System.out.println("DELAYED TIME: "+ newTime);
                System.out.println("RECORDED TIME: "+ metrics.snd);
                this.delay = true;
                this.updateMetrics(serverIP,Provider,jumps,newTime);
                return true;
            }
            return false;

        }finally {
            reentrantLock.unlock();
            this.readWriteLockMetrics.writeLock().unlock();
        }
    }


    public boolean checkDelay(){
        try{
            reentrantLock.lock();
            return delay;
        } finally {
            delay=false; // quando a thread sair o delay "fica resolvido"
            reentrantLock.unlock();
        }
    }




}
