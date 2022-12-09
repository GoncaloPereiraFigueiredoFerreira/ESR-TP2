package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Nodes.OverlayNode.ControlLayer.Exceptions.NonExistentValidRouteException;
import speedNode.Nodes.OverlayNode.Tables.IClientTable;
import speedNode.Nodes.OverlayNode.Tables.INeighbourTable;
import speedNode.Nodes.OverlayNode.Tables.IRoutingTable;
import speedNode.Utilities.ProtectedQueue;
import speedNode.Utilities.Serialize;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;
import speedNode.Utilities.Tuple;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RoutingHandler implements Runnable {
    private String bindAddress;
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;
    private Logger logger;
    private final ProtectedQueue<Tuple<String, Frame>> routingFramesQueue = new ProtectedQueue<>();
    private final Map<Integer, Deque<Frame>> requestsOnHold = new HashMap<>();
    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();
    private final Condition waitForActiveRoute = rwlock.readLock().newCondition();

    public RoutingHandler(String bindAddress, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable, Logger logger) {
        this.bindAddress = bindAddress;
        this.neighbourTable = neighbourTable;
        this.routingTable = routingTable;
        this.clientTable = clientTable;
        this.logger = logger;
    }

    public void pushRoutingFrame(String senderIP, Frame routingFrame){
        routingFramesQueue.pushElem(new Tuple<>(senderIP, routingFrame));
    }

    /* ************* Activate Best Route / Handle ACKS/NACKS *************** */

    //Nodes that either contacted or got contacted about activating a new route
    private final Set<String> nodesActivateRoute = new HashSet<>();
    private final Set<String> nodesAskedToActivateRoute = new HashSet<>();
    private boolean activateBestRouteActive = false;

    private void activateBestRoute(String requester, Collection<String> contacted) throws IOException {

        if(requester != null) {
            if (contacted != null) {
                if (contacted.contains(bindAddress)) {
                    sendActivateRouteResponse(false);
                    return;
                }
                nodesActivateRoute.addAll(contacted);
            }
            nodesActivateRoute.add(requester);
        }

        //Is an activate best route already in course?
        if(!activateBestRouteActive){

            activateBestRouteActive = true;

            //Not a server
            if(clientTable.getAllServers().size() == 0){
                Tuple<String,String> prevRoute = routingTable.getActiveRoute();

                //Calculates the best route excluding all the nodes in the given set
                Set<String> excludedNodes = new HashSet<>(nodesActivateRoute);
                excludedNodes.addAll(nodesAskedToActivateRoute);
                Tuple<String,String> newRoute = routingTable.activateBestRoute(excludedNodes);

                //If there is no valid route available throws exception
                if(newRoute == null){
                    sendActivateRouteResponse(false);
                    return;
                    //throw new NonExistentValidRouteException();
                }

                //Get ips of the neighbours belonging to the previous best route and the new best route
                String prevProvIP = prevRoute != null ? prevRoute.snd : null;
                String newProvIP = newRoute.snd;

                if(newProvIP.equals(prevProvIP)){
                    sendActivateRouteResponse(true);
                    return;
                }

                //Register node that is going to be contacted to activate the route
                nodesAskedToActivateRoute.add(newProvIP);

                //Requests the following neighbour in the new route to activate it
                List<String> newContacted = new ArrayList<>();
                if(contacted != null)
                    newContacted.addAll(contacted);
                newContacted.add(bindAddress);
                sendActivateRouteRequest(newProvIP, newContacted);
            }else {
                sendActivateRouteResponse(true);
            }
        }
    }

    private void handleActivateBestRouteResponse(Frame frame){
        if(frame == null) return;
        boolean response;

        try {
            response = Serialize.deserializeBoolean(frame.getData());
            if(response) {
                sendActivateRouteResponse(true);
                //TODO - Deactivate previous route
            }
            else
                activateBestRoute(null, null);

        }catch (IOException ioe){ return; }
    }

    private void sendActivateRouteRequest(String neighbourIP, List<String> contacted) throws IOException {
        TaggedConnection tc = neighbourTable.getConnectionHandler(neighbourIP).getTaggedConnection();
        tc.send(0, Tags.ACTIVATE_ROUTE, Serialize.serializeListOfStrings(contacted));
    }

    /**
     *
     * @param response
     * @throws IOException
     */
    private void sendActivateRouteResponse(boolean response){
        Collection<String> neighboursToContact = neighbourTable.getConnectedNeighbours()
                                                               .stream()
                                                               .filter(nodesActivateRoute::contains)
                                                               .collect(Collectors.toSet());

        for (String n : neighboursToContact) {
            try {
                TaggedConnection tc = neighbourTable.getConnectionHandler(n).getTaggedConnection();
                tc.send(0, Tags.RESPONSE_ACTIVATE_ROUTE, Serialize.serializeBoolean(response));
            }catch (IOException ignored){}
        }

        activateBestRouteActive = false;
        nodesActivateRoute.clear();
        nodesAskedToActivateRoute.clear();
        routeUpdateCond.signalAll();
    }


    /* **************************** */

    /**
     * Waits until a valid route is established
     */
    public void waitForActiveRoute() throws InterruptedException {
        try {
            rwlock.readLock().lock();
            while (routingTable.getActiveRoute() != null)
                waitForActiveRoute.await();
        }finally {
            rwlock.readLock().unlock();
        }
    }


    /* ******** Wait for route update ********* */

    private ReentrantLock routeUpdateLock = new ReentrantLock();
    private Condition routeUpdateCond = routeUpdateLock.newCondition();

    public void waitForRouteUpdate() {
        try {
            routeUpdateLock.lock();
            routeUpdateCond.await();
        } catch (InterruptedException ignored) {
        } finally { routeUpdateLock.unlock(); }
    }

    @Override
    public void run() {

    }

    /*
    private void activateBestRoute() throws IOException {

        //Is directly not connected to a server
        if (clientTable.getAllServers().size() == 0) {
            Tuple<String, String> prevRoute = routingTable.getActiveRoute();
            Tuple<String, String> newRoute = routingTable.activateBestRoute();

            var newProvidingIP = newRoute.snd;

            ConnectionHandler newProvCH = neighbourTable.getConnectionHandler(newProvidingIP);
            TaggedConnection newProvTC = newProvCH.getTaggedConnection();

            // Se não existia uma rota antes
            if (prevRoute == null) {
                newProvTC.send(0, Tags.ACTIVATE_ROUTE, new byte[]{});
            } else {
                var oldProvidingIP = prevRoute.snd;
                ConnectionHandler oldProvCH = neighbourTable.getConnectionHandler(oldProvidingIP);
                TaggedConnection oldProvTC = oldProvCH.getTaggedConnection();
                // se forem diferentes
                if (!newProvidingIP.equals(oldProvidingIP)) {
                    assert oldProvTC != null;
                    oldProvTC.send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
                    newProvTC.send(0, Tags.ACTIVATE_ROUTE, new byte[]{});
                }

            }
        }

        this.routingTable.printTables();
    }
    */
}
