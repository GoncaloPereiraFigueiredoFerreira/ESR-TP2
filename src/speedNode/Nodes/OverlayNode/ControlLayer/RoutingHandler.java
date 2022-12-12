package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Nodes.OverlayNode.Tables.IClientTable;
import speedNode.Nodes.OverlayNode.Tables.INeighbourTable;
import speedNode.Nodes.OverlayNode.Tables.IRoutingTable;
import speedNode.Utilities.ProtectedQueue;
import speedNode.Utilities.TaggedConnection.Serialize;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.TaggedConnection.Tags;
import speedNode.Utilities.Tuple;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class RoutingHandler implements Runnable {
    private final String nodeName;
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;
    private final Logger logger;
    private final ProtectedQueue<Tuple<String, Frame>> routingFramesQueue = new ProtectedQueue<>();

    public RoutingHandler(String nodeName, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable, Logger logger) {
        this.nodeName = nodeName;
        this.neighbourTable = neighbourTable;
        this.routingTable = routingTable;
        this.clientTable = clientTable;
        this.logger = logger;
    }

    @Override
    public void run() {
        logger.info("Routing handler started.");
        while(!Thread.currentThread().isInterrupted()){
            //Handle received frame
            handleRoutingFrame();

            if(this.routingTable.checkDelay())
                activateBestRoute(null, null);
        }
        logger.info("Routing handler stopped.");
    }

    private void handleRoutingFrame(){
        Tuple<String,Frame> tuple = this.routingFramesQueue.popElem(200, TimeUnit.MILLISECONDS);
        if(tuple != null) {
            String neighbourName = tuple.fst;
            Frame frame = tuple.snd;
            switch (frame.getTag()) {
                case Tags.ACTIVATE_ROUTE -> handleActivateRoute(neighbourName, frame);
                case Tags.DEACTIVATE_ROUTE -> handleDeactivateRoute(neighbourName);
                case Tags.RESPONSE_ACTIVATE_ROUTE -> handleActivateBestRouteResponse(neighbourName, frame);
                case Tags.RECOVER_ROUTE -> handleRecoverRoute(neighbourName);
            }
        }
    }

    /* ************* Activate Best Route / Handle ACKS/NACKS *************** */

    //Nodes that either contacted or got contacted about activating a new route
    private final Set<String> nodesActivateRoute = new HashSet<>();
    private final Set<String> nodesAskedToActivateRoute = new HashSet<>();
    private final Set<String> requesters = new HashSet<>();
    private boolean activateBestRouteActive = false;

    //Used when a change of route was requested and immediately after a deactivation of the active route,
    // not giving enough time for the response of the first request to get back. This bool is used to ignore
    // the response.
    private boolean deactivatedRoute = false;
    private String prevProvName = null; //Previous provider name

    private void activateBestRoute(String neighbourName, Collection<String> contacted) {
        if(neighbourName != null) {
            if (contacted != null) {
                if (contacted.contains(nodeName)) {
                    sendActivateRouteResponse(false);
                    return;
                }
                nodesActivateRoute.addAll(contacted);
            }
            nodesActivateRoute.add(neighbourName);
            requesters.add(neighbourName);
        }

        //Is an activate best route already in course?
        if(!activateBestRouteActive){
            System.out.println("Trying to activate best route");
            activateBestRouteActive = true;
            deactivatedRoute = false;

            //Not a server
            if(clientTable.getAllServers().size() == 0){
                Tuple<String,String> prevRoute = routingTable.getActiveRoute();

                //Calculates the best route excluding all the nodes in the given set
                Set<String> excludedNodes = new HashSet<>(nodesActivateRoute);
                excludedNodes.addAll(nodesAskedToActivateRoute);
                Tuple<String,String> newRoute = routingTable.activateBestRoute(excludedNodes);
                System.out.println("" + newRoute +  " | " + routingTable.getActiveRoute());

                //If there is no valid route available throws exception
                if(newRoute == null){
                    sendActivateRouteResponse(false);
                    return;
                    //throw new NonExistentValidRouteException();
                }

                //Get ips of the neighbours belonging to the previous best route and the new best route
                if(prevProvName == null && prevRoute != null)
                    prevProvName = prevRoute.snd;

                String newProvName = newRoute.snd;

                if(newProvName.equals(prevProvName))
                    prevProvName = null;

                //Register node that is going to be contacted to activate the route
                nodesAskedToActivateRoute.add(newProvName);

                //Requests the following neighbour in the new route to activate it
                List<String> newContacted = new ArrayList<>();
                if(contacted != null)
                    newContacted.addAll(contacted);
                newContacted.add(nodeName);

                try { sendActivateRouteRequest(newProvName, newContacted); }
                catch (IOException ioe){
                    //Tries to activate a new route, when requesting to activate route fails
                    activateBestRouteActive = false; //Necessary so a new iteration of activateBestRoute can happen
                    activateBestRoute(null, null);
                }
            }else {
                sendActivateRouteResponse(true);
            }
        }

        System.out.println("*** DEPOIS DE ACTIVATE ROUTE ***");
        routingTable.printTables(); //TODO - remover aqui
        neighbourTable.printTable();
    }

    private void deactivateRoute(String provider, String neighbourName){
        //neighbourName == null <=> the node itself

        //deactivate route if the neighbour is the only one wanting the stream
        if(neighbourName != null)
            neighbourTable.updateWantsStream(neighbourName, false);

        //Requests the provider to deactivate the route, if the node itself wants to close the route
        // or if there are no neighbours wanting the stream
        if(neighbourName == null || neighbourTable.getNeighboursWantingStream().size() == 0){
            if(provider != null){
                try{
                    TaggedConnection tc = neighbourTable.getConnectionHandler(provider).getTaggedConnection();
                    tc.send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
                }catch (IOException ignored){}
            }

            routingTable.deactivateRoute(provider);
        }

        System.out.println("*** DEPOIS DE DEACTIVATE ROUTE ***");
        routingTable.printTables(); //TODO - remover aqui
        neighbourTable.printTable();
    }

    private void handleActivateBestRouteResponse(String neighbourName, Frame frame){
        if(frame == null) return;
        boolean response;

        try {
            if(deactivatedRoute) sendActivateRouteResponse(false);
            else {
                Tuple<String, String> activeRoute = routingTable.getActiveRoute();
                response = Serialize.deserializeBoolean(frame.getData());

                //If the node is not connected to servers and some node sent a response, then it is ignored
                // since a node attached to a server does not ask for a route
                //Or if no route is active
                //Or if the neighbour that sent the package does not belong to the current route
                if(clientTable.hasServers()){
                    if(neighbourName != null && response) {
                        try {
                            TaggedConnection tc = neighbourTable.getConnectionHandler(neighbourName).getTaggedConnection();
                            tc.send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
                        } catch (Exception ignored) {
                        }
                        sendActivateRouteResponse(true);
                    }
                    return;
                } else if (activeRoute == null || !activeRoute.snd.equals(neighbourName)) {
                    try {
                        TaggedConnection tc = neighbourTable.getConnectionHandler(neighbourName).getTaggedConnection();
                        tc.send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
                    }catch (Exception ignored){}
                    return;
                }

                System.out.println("Response a activate best route recebida: bool:" + response);

                if (response) {
                    sendActivateRouteResponse(true);
                    System.out.println("Desativando rota anterior : " + prevProvName);
                    deactivateRoute(prevProvName, null);

                    //Update previous provider IP to the current active route
                    prevProvName = activeRoute.snd;
                } else {
                    //remove routes from the node that sent the false response
                    routingTable.removeRoutes(neighbourName);
                    activateBestRouteActive = false;
                    activateBestRoute(null, null);
                }
            }
        }catch (IOException ioe){ return; }
    }

    /*
    private void handleActivateBestRouteResponse(Frame frame){
        if(frame == null) return;
        boolean response;

        try {
            if(deactivatedRoute) sendActivateRouteResponse(false);
            else {
                response = Serialize.deserializeBoolean(frame.getData());
                System.out.println("Response a activate best route recebida: bool:" + response);

                if (response) {
                    sendActivateRouteResponse(true);
                    System.out.println("Desativando rota anterior : " + prevProvName);
                    deactivateRoute(prevProvName, null);

                    //Update previous provider IP to the current active route
                    var activeRoute = routingTable.getActiveRoute();
                    if (activeRoute != null) prevProvName = activeRoute.snd;
                } else {
                    activateBestRouteActive = false;
                    activateBestRoute(null, null);
                }
            }
        }catch (IOException ioe){ return; }
    }*/

    private void handleActivateRoute(String neighbourName,Frame frame){
        List<String> contacted = null;
        try {
            contacted = Serialize.deserializeListOfStrings(frame.getData());
        } catch (IOException ignored) {}
        System.out.println("Nodos contactados: " + contacted);
        activateBestRoute(neighbourName, contacted);
    }

    private void handleDeactivateRoute(String neighbourName) {
        deactivatedRoute = true;
        Tuple<String,String> activeRoute = routingTable.getActiveRoute();
        String provider = activeRoute != null ? activeRoute.snd : null;
        deactivateRoute(provider, neighbourName);
        deactivateRoute(prevProvName, null); //Deactivates the previous route if it exists
    }

    private void sendActivateRouteRequest(String neighbourName, List<String> contacted) throws IOException {
        try {
            System.out.println("SENDING ACTIVATE ROUTE REQUEST TO " + neighbourName);
            TaggedConnection tc = neighbourTable.getConnectionHandler(neighbourName).getTaggedConnection();
            tc.send(0, Tags.ACTIVATE_ROUTE, Serialize.serializeListOfStrings(contacted));
        }catch (IOException ioe){
            activateBestRouteActive = false;
            activateBestRoute(null, null);
        }
    }

    /**
     *
     * @param response
     * @throws IOException
     */
    private void sendActivateRouteResponse(boolean response){
        try {
            Collection<String> neighboursToContact = neighbourTable.getNeighbours()
                    .stream()
                    .filter(requesters::contains)
                    .collect(Collectors.toSet());

            for (String n : neighboursToContact) {
                try {
                    System.out.println("SENDING ACTIVATE ROUTE RESPONSE TO " + n);
                    TaggedConnection tc = neighbourTable.getConnectionHandler(n).getTaggedConnection();
                    tc.send(0, Tags.RESPONSE_ACTIVATE_ROUTE, Serialize.serializeBoolean(response));
                    if (response) neighbourTable.updateWantsStream(n, true);
                } catch (Exception ignored) {}
            }
        }finally {
            activateBestRouteActive = false;
            nodesActivateRoute.clear();
            nodesAskedToActivateRoute.clear();
            requesters.clear();
            try {
                routeUpdateLock.lock();
                routeUpdateCond.signalAll();
                System.out.println("\nSIGNALED ROUTE UPDATE\n");
            }finally { routeUpdateLock.unlock(); }

            System.out.println("*** DEPOIS DE SEND ACTIVATE ROUTE RESPONSE ***");
            routingTable.printTables(); //TODO - remover aqui
            neighbourTable.printTable();
        }
    }

    /**
     * Recover Route should be called when the active route to the server is lost.
     * @param neighbourName Provider of the stream. A neighbour that "died"
     *                      or that could not recover a route to the server.
     */
    private void handleRecoverRoute(String neighbourName){

        //Gets the current active route
        Tuple<String, String> activeRoute = routingTable.getActiveRoute();

        //remove routes from the neighbour
        routingTable.removeRoutes(neighbourName);

        //If the active route is no longer using the neighbour as provider,
        // then there is nothing to do
        if(activeRoute != null && !activeRoute.snd.equals(neighbourName))
            return;

        //TODO - activateBestRoute deve remover rotas de quem nao consegue ativar, e quando nao tiver rotas enviar recover route frame se nao conseguir ativar nenhuma rota

        //Activates best route
        activateBestRoute(null, null);
    }


    /* ******** Wait for route update ********* */

    private ReentrantLock routeUpdateLock = new ReentrantLock();
    private Condition routeUpdateCond = routeUpdateLock.newCondition();

    public void waitForRouteUpdate() {
        try {
            routeUpdateLock.lock();
            routeUpdateCond.await();
            System.out.println("\nAWAKED FROM ROUTE UPDATE: " + routingTable.getActiveRoute() + "\n");
        } catch (InterruptedException ignored) {
        } finally { routeUpdateLock.unlock(); }
    }

    public void pushRoutingFrame(String neighbourName, Frame routingFrame){
        routingFramesQueue.pushElem(new Tuple<>(neighbourName, routingFrame));
    }
}
