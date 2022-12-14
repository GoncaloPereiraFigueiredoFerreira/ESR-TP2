package speedNode.Nodes.OverlayNode.Tables;

import speedNode.Utilities.Tuple;
import java.util.*;
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
    private final HashMap<Tuple<String,String>, Tuple<Integer,Long>> metricsTable = new HashMap<>();
    private Tuple<String,String> activeRoute = null;

    /**
     *  IP of the providing neighbour | List of the servers that can be accessed by the neighbour
     */
    private final HashMap<String, List<String>> providers = new HashMap<>();

    private final ReadWriteLock readWriteLockMetrics = new ReentrantReadWriteLock();
    private final ReadWriteLock readWriteLockActive = new ReentrantReadWriteLock();
    private final ReadWriteLock readWriteLockProviders = new ReentrantReadWriteLock();

    private boolean delay = false;
    private final ReentrantLock reentrantLock = new ReentrantLock();

    private int packetCounter=0; //Just a test
    private int delayCounter=0;

    public RoutingTable(){

    }

    @Override
    public boolean addServerPath(String ServerIp, String Provider, int Jumps, long Time, boolean active) {
        Tuple<String, String> temp = new Tuple<>(ServerIp, Provider);
        try {
            readWriteLockProviders.writeLock().lock();
            if (this.providers.containsKey(Provider) && this.providers.get(Provider).contains(ServerIp)) return false;
            else{
                try {
                    readWriteLockMetrics.writeLock().lock();

                    if (this.providers.containsKey(Provider))
                        this.providers.get(Provider).add(ServerIp);
                    else this.providers.put(Provider,new ArrayList<>(Arrays.asList(ServerIp)));

                    this.metricsTable.put(temp,new Tuple<>(Jumps,Time));
                    return true;

                }finally {
                    readWriteLockMetrics.writeLock().unlock();
                }
            }
        }finally {
            readWriteLockProviders.writeLock().unlock();
        }
    }

    @Override
    public boolean updateMetrics(String ServerIp, String provider, int Jumps, long Time) {
        Tuple<String, String> temp = new Tuple<>(ServerIp, provider);
        try {
            readWriteLockMetrics.writeLock().lock();
            if (!this.metricsTable.containsKey(temp)) return false;
            else{
                Tuple<Integer,Long> temp2 = new Tuple<>(Jumps,Time);
                this.metricsTable.put(temp,temp2);
                return true;
            }

        }finally {
            readWriteLockMetrics.writeLock().unlock();
        }
    }

    public boolean activateRoute(Tuple<String,String> route){
        try {
            readWriteLockMetrics.readLock().lock();
            readWriteLockActive.writeLock().lock();
            if (!this.metricsTable.containsKey(route))
                return false;
            else{
                activeRoute = route;
                return true;
            }
        }finally {
            readWriteLockMetrics.readLock().unlock();
            readWriteLockActive.writeLock().unlock();
        }
    }

    @Override
    public boolean activateRoute(String ServerIp, String Provider) {
        Tuple<String, String> temp = new Tuple<>(ServerIp, Provider);
        return activateRoute(temp);
    }


    public void deactivateRoute(){
        try{
            readWriteLockActive.writeLock().lock();
            activeRoute = null;
        }finally {
            readWriteLockActive.writeLock().unlock();
        }
    }


    public void deactivateRoute(String provider){
        try{
            readWriteLockActive.writeLock().lock();
            if(activeRoute != null && activeRoute.snd.equals(provider))
                activeRoute = null;
        }finally {
            readWriteLockActive.writeLock().unlock();
        }
    }


    @Override
    public boolean isRouteActive(String ServerIp, String Provider) {
        try{
            this.readWriteLockActive.readLock().lock();
            Tuple<String,String> t = new Tuple<>(ServerIp,Provider);
            return t.equals(activeRoute);
        }finally {
            this.readWriteLockActive.readLock().unlock();
        }
    }

    @Override
    public Tuple<Integer,Long> getMetrics(String ServerIp, String Provider) {
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
            return activeRoute;
        }finally {
            this.readWriteLockActive.readLock().unlock();
        }
    }

    @Override
    public Tuple<String, String> activateBestRoute() {
        try{
            this.readWriteLockMetrics.writeLock().lock();
            float wiggleRoom = 0.15f; // todo - VALORIZAR MAIS OS SALTOS
            long score;
            long minScore = Long.MAX_VALUE;
            Tuple<String,String> bestRoute =null;



            for (Map.Entry<Tuple<String,String>,Tuple<Integer,Long>> entry : this.metricsTable.entrySet()){
                score = entry.getValue().snd + (long) (entry.getValue().fst * wiggleRoom);
                if (score < minScore) {
                    minScore= score;
                    bestRoute = entry.getKey();
                }
            }

            if(bestRoute != null && activateRoute(bestRoute.fst,bestRoute.snd))
                return bestRoute.clone();
            else
                return null;
        }finally {
            this.readWriteLockMetrics.writeLock().unlock();
        }
    }

    public Tuple<String, String> getBestRoute(Set<String> excluded) {
        try{
            this.readWriteLockMetrics.readLock().lock();
            float wiggleRoom = 0.15f; // todo - VALORIZAR MAIS OS SALTOS
            long score;
            long minScore = Long.MAX_VALUE;
            Tuple<String,String> bestRoute =null;

            for (Map.Entry<Tuple<String,String>,Tuple<Integer,Long>> entry : this.metricsTable.entrySet()){
                if (!excluded.contains(entry.getKey().snd) ) { // if neighbour isn't excluded from being the best route
                    score = entry.getValue().snd + (long) (entry.getValue().fst * wiggleRoom);
                    System.out.println("SCORE for neighbour: " + entry.getKey() + " -> " + score);
                    if (score < minScore) {
                        minScore = score;
                        bestRoute = entry.getKey();
                        System.out.println("Melhor score: " + entry.getKey());
                    }
                }
            }

            if(bestRoute != null)
                return bestRoute.clone();
            else
                return null;
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }

    public Tuple<String, String> activateBestRoute(Set<String> excluded) {
        try{
            this.readWriteLockMetrics.writeLock().lock();
            Tuple<String, String> bestRoute = getBestRoute(excluded);
            if(bestRoute != null && activateRoute(bestRoute.fst,bestRoute.snd))
                return bestRoute;
            else
                return null;
        }finally {
            this.readWriteLockMetrics.writeLock().unlock();
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
            this.readWriteLockProviders.readLock().lock();
            this.readWriteLockMetrics.readLock().lock();
            this.readWriteLockActive.readLock().lock();

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
                System.out.println("\tserver: " + tupleNodes.fst + " | neighbour: " + tupleNodes.snd + " | jumps: " + tupleMetrics.fst + " | time: " + tupleMetrics.snd);
            }
            System.out.println("}\n");

            System.out.println("Active Route: " + activeRoute);

            System.out.println("\n**********************************\n\n");
        }finally {
            this.readWriteLockProviders.readLock().unlock();
            this.readWriteLockMetrics.readLock().unlock();
            this.readWriteLockActive.readLock().unlock();
        }
    }

    @Override
    public boolean updateMetricsAndCheckDelay(String serverIp, String provider, int jumps, long newTime) {
        Tuple<String, String> temp = new Tuple<>(serverIp, provider);
        try {
            readWriteLockMetrics.writeLock().lock();
            if (!this.metricsTable.containsKey(temp)) return false;
            else{
                var metrics = this.metricsTable.get(new Tuple<>(serverIp, provider));

                var diff = newTime - metrics.snd;
                var margin = metrics.snd * 0.5;
                packetCounter++;

                //TODO - criar funcao para calcular margem
                if (diff > margin && newTime> 50 * 1000 * 1000 && packetCounter> 15){
                    System.out.println("ROUTING TABLE: DELAY DETETADO");
                    System.out.println("DELAYED TIME: "+ newTime /1000 );
                    System.out.println("RECORDED TIME: "+ metrics.snd/1000);
                    delayCounter++;
                    if (delayCounter > 5) {
                        Tuple<Integer, Long> temp2 = new Tuple<>(jumps, newTime);
                        this.metricsTable.put(temp, temp2);
                        packetCounter = 0;
                        delayCounter = 0;
                        return true;
                    }
                    else return false;
                }
                else if(diff < -margin)  {
                    Tuple<Integer,Long> temp2 = new Tuple<>(jumps,newTime);
                    this.metricsTable.put(temp,temp2);
                }
                delayCounter=0;
                return false;
            }

        }finally {
            readWriteLockMetrics.writeLock().unlock();
        }
    }

    @Override
    public void signalDelay(){
        try{
            reentrantLock.lock();
            this.delay = true;
        }finally { reentrantLock.unlock(); }
    }

    @Override
    public boolean verifyDelay(String serverIP, String provider,int jumps ,long newTime) {
        // Fazer conta para detetar delay
        // se foi detetado signalAll
        try{
            this.readWriteLockMetrics.writeLock().lock();
            reentrantLock.lock();

            var metrics = this.metricsTable.get(new Tuple<>(serverIP, provider));
            //System.out.println("New time: " + newTime/1000 + " | Oldtime: "  + metrics.snd/1000 );
            if (newTime - metrics.snd >  metrics.snd &&
                newTime > 200 * 1000 * 1000){
                System.out.println("ROUTING TABLE: DELAY DETETADO");
                System.out.println("DELAYED TIME: "+ newTime /1000 );
                System.out.println("RECORDED TIME: "+ metrics.snd/1000) ;
                this.delay = true;
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
            delay = false; // quando a thread sair o delay "fica resolvido"
            reentrantLock.unlock();
        }

    }

    public void removeRoutes(String neighbourName){
        try{
            this.readWriteLockProviders.writeLock().lock();
            this.readWriteLockMetrics.writeLock().lock();
            this.readWriteLockActive.writeLock().lock();

            for (Tuple<String,String> key : new ArrayList<>(this.metricsTable.keySet())){
                if(key.snd.equals(neighbourName)) this.metricsTable.remove(key);
            }

            if(activeRoute != null && activeRoute.snd.equals(neighbourName))
                activeRoute = null;

            this.providers.remove(neighbourName);
        }finally {
            this.readWriteLockProviders.writeLock().unlock();
            this.readWriteLockMetrics.writeLock().unlock();
            this.readWriteLockActive.writeLock().unlock();
        }
    }

    public boolean containsRoutes(){
        try{
            this.readWriteLockMetrics.readLock().lock();
            return this.metricsTable.size() != 0;
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }

    public boolean containsRoutes(Collection<String> excluded){
        try{
            this.readWriteLockMetrics.readLock().lock();
            for(var entry : metricsTable.keySet())
                if(!excluded.contains(entry.snd))
                    return true;
            return false;
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }

    public Set<String> additionalProviders(Collection<String> excluded){
        try{
            Set<String> set = new HashSet<>();
            this.readWriteLockMetrics.readLock().lock();
            for(var entry : metricsTable.keySet())
                if(!excluded.contains(entry.snd))
                    set.add(entry.snd);
            return set;
        }finally {
            this.readWriteLockMetrics.readLock().unlock();
        }
    }
}
