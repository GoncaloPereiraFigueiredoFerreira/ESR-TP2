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
    //private boolean deactivatedRoute = false;
    private String prevProvName = null; //Previous provider name

    /**
     * Tries to activate the best route. Only performed if an activation of the best route is not already in execution.
     * @param neighbourName Requester of the activation. Name of the neighbour, or "null" if the node itself requested the activation.
     * @param contacted Like a route pinning, in order to avoid loops.
     */
    private void activateBestRoute(String neighbourName, Collection<String> contacted) {
        if(neighbourName != null)
            logger.info(neighbourName + " requested activation of best route. Contacted nodes: " + contacted);
        else
            logger.info("Self request of activation of best route.");

        //If the neighbourName == null, then the node itself
        // requested the activation of the best route.
        if(neighbourName != null) {
            if (contacted != null) {
                if (contacted.contains(nodeName)) {
                    logger.info("Loop detected. Wont activate a new best route.");
                    sendActivateRouteResponse(false);
                    return;
                }
                nodesActivateRoute.addAll(contacted);
            }
            nodesActivateRoute.add(neighbourName);
            requesters.add(neighbourName);
        }

        //Checks if an activation of the best route is already in course
        if(!activateBestRouteActive){
            activateBestRouteActive = true;
            //deactivatedRoute = false;

            //Not a server
            if(!clientTable.hasServers()){
                logger.info("Not a server. Trying to activate best route.");
                Tuple<String,String> prevRoute = routingTable.getActiveRoute();

                //Calculates the best route excluding all the nodes in the given set
                Set<String> excludedNodes = new HashSet<>(nodesActivateRoute);
                excludedNodes.addAll(nodesAskedToActivateRoute);
                Tuple<String,String> newRoute = routingTable.activateBestRoute(excludedNodes);

                //If there is no valid route available throws exception
                if(newRoute == null){
                    logger.info("Could not find a best route.");
                    if(!requesters.isEmpty()) {
                        sendActivateRouteResponse(false);
                        return;
                    }
                    //TODO - recovery mode
                    return;
                }

                logger.info("Trying to activate the route " + newRoute + ". Previous route is " + prevRoute);

                //Registers the name of the previous provider,
                // so it can be used to cancel the route,
                // when the new one is activated
                if(prevProvName == null && prevRoute != null)
                    prevProvName = prevRoute.snd;

                String newProvName = newRoute.snd;

                //If the best route is still the same,
                // sets the 'prevProvName' to null
                // to avoid the deactivation of the route
                if(newProvName.equals(prevProvName))
                    prevProvName = null;

                //Registers node that is going to be contacted to activate the route
                // in order to avoid the repetition of the same request
                nodesAskedToActivateRoute.add(newProvName);

                //Adds the name of the node to the route that the request travelled
                List<String> newContacted = new ArrayList<>();
                if(contacted != null) newContacted.addAll(contacted);
                newContacted.add(nodeName);

                //Requests the neighbour (next node in the new route) to activate it
                try { sendActivateRouteRequest(newProvName, newContacted); }
                catch (IOException ioe){
                    //Tries to activate a new route, when requesting to activate route fails
                    logger.info("Activating the new route failed. Will try again.");
                    activateBestRouteActive = false; //Necessary so a new iteration of activateBestRoute can happen
                    activateBestRoute(null, null);
                }
            }else {
                logger.info("Is server. Accepting activation of route...");
                sendActivateRouteResponse(true);
            }
        } else logger.info("Activation of best route already in course.");

        logger.info("Exiting the method: Activation Of Best Route");
    }

    /**
     * If the provider is still providing the stream and if the neighbour is the only one
     * wanting the stream, deactivates the route. The route is also deactivate if the node itself
     * demands it.
     * @param provider Neighbour node that provides the stream
     * @param neighbourName Neighbour that requested the deactivation of the route.
     *                      If 'null' then the node itself requested the deactivation of the route.
     */
    private void deactivateRoute(String provider, String neighbourName) {
        //Neighbour requested the deactivation, therefore it no longer wants the stream
        if (neighbourName != null) {
            logger.info(neighbourName + " no longer wants the stream.");
            neighbourTable.updateWantsStream(neighbourName, false);
        }

        //Requests the provider to deactivate the route,
        // if the node itself wants to close the route
        // or if there are no neighbours wanting the stream
        if (neighbourName == null || !neighbourTable.anyNeighbourWantsTheStream()) {
            try {
                TaggedConnection tc = neighbourTable.getConnectionHandler(provider).getTaggedConnection();
                tc.send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
                logger.info("Sent deactivate route frame to " + neighbourName);
            } catch (Exception ignored) {}
            routingTable.deactivateRoute(provider);
            logger.info("Route from provider " + provider + " deactivated.");
        }
        else logger.info("No route was deactivated!");
    }

    /**
     * Handles a frame that contains the response to the request of activating the best route.
     * If a request of activation of the best route was undergoing when the deactivation of the current route happened,
     * then the response should be discarded and the message that the activation of the route could not be performed
     * should be propragated to the requesters.
     * @param neighbourName Name of the neighbour that sent the response
     * @param frame Activate Best Route Response Frame
     */
    private void handleActivateBestRouteResponse(String neighbourName, Frame frame){
        if(frame == null) return;
        boolean response;

        try {
            //if(deactivatedRoute) {
            //    logger.info("Received response to the request of activating the best route after a request to close the route.");
            //    sendActivateRouteResponse(false);
            //}
            //else {
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

                //System.out.println("Response a activate best route recebida: (" + response + ")");

                if (response) {
                    sendActivateRouteResponse(true);
                    //System.out.println("Desativando rota anterior : " + prevProvName);
                    deactivateRoute(prevProvName, null);

                    //Update previous provider IP to the current active route
                    prevProvName = activeRoute.snd;
                } else {
                    //remove routes from the node that sent the false response
                    routingTable.removeRoutes(neighbourName);
                    activateBestRouteActive = false;
                    activateBestRoute(null, null);
                }
            //}
        }catch (IOException ioe){ return; }
    }

    private void handleActivateRoute(String neighbourName,Frame frame){
        List<String> contacted = null;
        try {
            contacted = Serialize.deserializeListOfStrings(frame.getData());
        } catch (IOException ignored) {}
        //System.out.println("Nodos contactados (ACTIVATE ROUTE): " + contacted);
        activateBestRoute(neighbourName, contacted);
    }

    private void handleDeactivateRoute(String neighbourName) {

        // If an activate route request is active, and if the neighbour, that requested the
        //deactivation of the route, was one of the requesters, removes him.
        if(activateBestRouteActive){
            boolean contained = requesters.remove(neighbourName);

            if(contained)
                logger.info("Removed " + neighbourName + " from the new route activation requesters.");

            //if the neighbour was the only requester, and if the node does not have
            // clients, then a new activation of best route can be requested
            if(contained && requesters.size() == 0 && !clientTable.hasClients()){
                logger.info("Node does not have clients, and was the only requester, so a new route activation is allowed.");
                activateBestRouteActive = false;
                nodesActivateRoute.clear();
                nodesAskedToActivateRoute.clear();
                requesters.clear();
            }
        }

        //deactivatedRoute = true;
        Tuple<String,String> activeRoute = routingTable.getActiveRoute();
        String provider = activeRoute != null ? activeRoute.snd : null;
        deactivateRoute(provider, neighbourName);
        deactivateRoute(prevProvName, null); //Deactivates the previous route if it exists
    }

    private void sendActivateRouteRequest(String neighbourName, List<String> contacted) throws IOException {
        try {
            //System.out.println("SENDING ACTIVATE ROUTE REQUEST TO " + neighbourName);
            TaggedConnection tc = neighbourTable.getConnectionHandler(neighbourName).getTaggedConnection();
            tc.send(0, Tags.ACTIVATE_ROUTE, Serialize.serializeListOfStrings(contacted));
            logger.info("Sent activate route request to " + neighbourName + ". Contacted nodes: " + contacted);
        }catch (Exception e){
            logger.info("Could not send route request to " + neighbourName);
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

            logger.info("Sending activate route response to " + neighboursToContact);

            for (String n : neighboursToContact) {
                try {
                    //System.out.println("SENDING ACTIVATE ROUTE RESPONSE TO " + n);
                    TaggedConnection tc = neighbourTable.getConnectionHandler(n).getTaggedConnection();
                    tc.send(0, Tags.RESPONSE_ACTIVATE_ROUTE, Serialize.serializeBoolean(response));

                    if (response) {
                        logger.info("Sent positive activate route response to " + n);
                        neighbourTable.updateWantsStream(n, true);
                    }else logger.info("Sent negative activate route response to " + n);

                } catch (Exception ignored) {}
            }
        }finally {
            logger.info("Activation of route is unlocked.");

            activateBestRouteActive = false;
            nodesActivateRoute.clear();
            nodesAskedToActivateRoute.clear();
            requesters.clear();
            try {
                routeUpdateLock.lock();
                routeUpdateCond.signalAll();
                //System.out.println("\nSIGNALED ROUTE UPDATE\n");
            }finally { routeUpdateLock.unlock(); }

            //System.out.println("*** ACABADO SEND ACTIVATE ROUTE RESPONSE ***");
        }
    }

    /**
     * Recover Route should be called when the active route to the server is lost.
     * @param neighbourName Provider of the stream. A neighbour that "died"
     *                      or that could not recover a route to the server.
     */
    private void handleRecoverRoute(String neighbourName){

        logger.info("Checking if a recover route is needed because of " + neighbourName + "'s death.");

        //Gets the current active route
        Tuple<String, String> activeRoute = routingTable.getActiveRoute();

        //remove routes from the neighbour
        routingTable.removeRoutes(neighbourName);

        //If the active route is no longer using the neighbour as provider,
        // then there is nothing to do
        if(activeRoute != null && !activeRoute.snd.equals(neighbourName)) {
            logger.info("Recover route not needed because " + neighbourName + " is not the provider.");
            return;
        }
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
            //System.out.println("\nAWAKED FROM ROUTE UPDATE: " + routingTable.getActiveRoute() + "\n");
        } catch (InterruptedException ignored) {
        } finally { routeUpdateLock.unlock(); }
    }

    public void pushRoutingFrame(String neighbourName, Frame routingFrame){
        routingFramesQueue.pushElem(new Tuple<>(neighbourName, routingFrame));
    }
}
