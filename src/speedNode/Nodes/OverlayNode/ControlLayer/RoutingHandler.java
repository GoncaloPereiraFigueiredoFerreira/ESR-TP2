package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Nodes.OverlayNode.ControlLayer.SpecializedFrames.*;
import speedNode.Nodes.OverlayNode.Tables.IClientTable;
import speedNode.Nodes.OverlayNode.Tables.INeighbourTable;
import speedNode.Nodes.OverlayNode.Tables.IRoutingTable;
import speedNode.Utilities.ProtectedQueue;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.TaggedConnection.Tags;
import speedNode.Utilities.Tuple;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class RoutingHandler implements Runnable {
    private final String nodeName;
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;
    private final Logger logger;
    private final SortedSet<String> sortedSet = new TreeSet<>();
    private final ProtectedQueue<BaseFrame> otherFramesQueue = new ProtectedQueue<>(new PriorityQueue<>());
    private final ProtectedQueue<BaseFrame> responseFramesQueue = new ProtectedQueue<>(new PriorityQueue<>());
    private boolean waitForResponse = false;
    private final ReentrantLock queuesLock = new ReentrantLock(true);
    private final Condition queuesCond = queuesLock.newCondition();

    public RoutingHandler(String nodeName, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable, Logger logger) {
        this.nodeName = nodeName;
        this.neighbourTable = neighbourTable;
        this.routingTable = routingTable;
        this.clientTable = clientTable;
        this.logger = logger;
    }

    //TODO - tirar scanner

    public void printQueues(){
        System.out.println("responsesQueue: " + responseFramesQueue.printQueue());
        System.out.println("otherFramesQueue: " + otherFramesQueue.printQueue());
        System.out.println("waitForResponse: " + waitForResponse);
    }


    /* ******* Frame insertion into/removal from queues ******* */

    /**
     * Returns a frame, giving priority to responses. If the flag "waitForResponse" is active, even if
     * the otherFramesQueue has frames, a null will be returned.
     * @return tuple. First element represents if the frame (second element) is a response.
     * @throws InterruptedException
     */
    private Tuple<Boolean,BaseFrame> pollFrame() throws InterruptedException {
        try {
            queuesLock.lock();

            BaseFrame bf = responseFramesQueue.pollElem(100, TimeUnit.MILLISECONDS);
            if(bf != null) {
                return new Tuple<>(responseFramesQueue.length() != 0, bf);
            }
            else {
                bf = otherFramesQueue.pollElem(false);
                return new Tuple<>(false, bf);
            }
        }finally {
            queuesLock.unlock();
        }
    }

    private BaseFrame pollFromResponsesQueue(){
        try {
            queuesLock.lock();
            return responseFramesQueue.pollElem(false);
        }finally {
            queuesLock.unlock();
        }
    }

    private BaseFrame pollFromOtherFramesQueue(){
        try {
            queuesLock.lock();
            return otherFramesQueue.pollElem(false);
        }finally {
            queuesLock.unlock();
        }
    }

    private void insertIntoResponseFramesQueue(BaseFrame bf){
        if(bf == null) {
            return;
        }
        try {
            queuesLock.lock();
            responseFramesQueue.pushElem(bf);
            queuesCond.signalAll();
        }finally { queuesLock.unlock(); }
    }

    private void insertIntoOtherFramesQueue(BaseFrame bf){
        if(bf == null) {
            return;
        }
        try {
            queuesLock.lock();
            otherFramesQueue.pushElem(bf);
            queuesCond.signalAll();
        }finally { queuesLock.unlock(); }
    }
    
    /* ******* Main function ******* */

    @Override
    public void run() {
        logger.info("Routing handler started.");
        while(true) {
            // Responses have maximum priority, so the thread wont exist the next loop until
            // a frame that is not a response is polled
            Tuple<Boolean,BaseFrame> tuple;
            boolean flag = true;
            while (flag){
                try { tuple = pollFrame(); }
                catch (InterruptedException e) { return; }
                flag = tuple.fst;
                handleRoutingFrame(tuple.snd);
            }

            if(this.routingTable.checkDelay())
                insertIntoOtherFramesQueue(new ActivateRouteRequestFrame(true));
        }
        //logger.info("Routing handler stopped.");
    }

    private void handleRoutingFrame(BaseFrame bf){
        //Handle Activate Route Request Frame
        if(bf instanceof ActivateRouteRequestFrame){
            ActivateRouteRequestFrame arReqFrame = (ActivateRouteRequestFrame) bf;
            boolean handled = activateRoute(arReqFrame);
            if(!handled) insertIntoOtherFramesQueue(arReqFrame);
        } else if (bf instanceof ActivateRouteResponseFrame) {
            ActivateRouteResponseFrame arRespFrame = (ActivateRouteResponseFrame) bf;
            handleActivateRouteResponse(arRespFrame);
        }else if (bf instanceof DeactivateRouteFrame) {
            DeactivateRouteFrame drf = (DeactivateRouteFrame) bf;
            handleDeactivateRoute(drf);
        }else if (bf instanceof RecoverRouteFrame) {
            RecoverRouteFrame rrf = (RecoverRouteFrame) bf;
            handleRecoverRoute(rrf);
        }
    }

    /* ******* Route activation ******* */

    private boolean activateRouteInCourse = false;
    private String lastNodeRequested = null; //Last node that a request to activate/update route was sent
    private Long timestampLastARR = null; //timestamp of the last activate route request
    private Set<String> requesters = new HashSet<>();
    private Set<String> contactedNodes = new HashSet<>();
    private Tuple<String,String> newRoute = null; //New Route that is being tested
    private boolean recoverRoute = false; //used in case a recover route was issued, if a new route could not be activated then,
                                          // all the neighbours receiving the stream from this node should be notified to recover the route

    /**
     *
     * @param arReqFrame Activate Route Request Frame
     * @return true if the frame was handled correctly. 
     * Otherwise, false, meaning the frame should be inserted in the queue, 
     * to be handled again after a response has been received.
     * A self request (arReqFrame.neighbourName == null) must not go to the queue again.
     */
    private boolean activateRoute(ActivateRouteRequestFrame arReqFrame){
        String neighbourName = arReqFrame.neighbourName;

        if(neighbourName != null)
            logger.info(neighbourName + " requested activation of best route. Contacted nodes: " + arReqFrame.contactedNodes);
        else
            logger.info("Self request of activation of best route.");

        //Is a server
        if(clientTable.hasServers()){
            //if the requester is a neighbour (not a self request)
            if(neighbourName != null) {
                //Marks the neighbour has someone that wants the stream
                neighbourTable.wantsStream(neighbourName);
                //Sends response informing that the stream was activated
                sendActivateRouteResponse(List.of(neighbourName), true);
                clearAllRequestVariables();
            }
            return true;
        }
        else{ //If it is not a server

            //Performs the reunion of the nodes that were already contacted by this node, the nodes contacted by the neighbour and the neighbours receiving the stream
            Set<String> reunionOfContactedNodes = new HashSet<>(contactedNodes);
            List<String> neighboursWantingStream = neighbourTable.getNeighboursWantingStream();
            reunionOfContactedNodes.addAll(arReqFrame.contactedNodes);
            reunionOfContactedNodes.addAll(neighboursWantingStream);

            //Uses the collection above to find a compatible route for both nodes (without loops)
            Set<String> additionalProviders = routingTable.additionalProviders(reunionOfContactedNodes);

            //True if there are no additional routes that do not use the requester as provider
            boolean noMoreRoutes = additionalProviders.size() == 0;

            //Handles the activate route request received from a neighbour imediatelly
            // after an activate route request has been sent to the same neighbour.
            Boolean handledParallelProblem = handleSameNodeParallelSentReceivedRequest(neighbourName,
                    noMoreRoutes, reunionOfContactedNodes, arReqFrame);
            if(handledParallelProblem != null) return handledParallelProblem;

            //If a neighbour requested the activation of a route (not the update), then a positive response can be given right away
            // if there is any route active
            if(neighbourName != null && !arReqFrame.updateRouteMode && routingTable.getActiveRoute() != null) {
                sendActivateRouteResponse(List.of(neighbourName), true);
                return true;
            }

            if(!activateRouteInCourse){
                //Sets the flag to true, to avoid the repetition of this process, because of another request
                activateRouteInCourse = true;

                //If no more routes are available, then the node cannot activate a route
                if(noMoreRoutes){
                    //Removes from the requesters the nodes wanting the stream, since they should be warned with a recover route
                    neighboursWantingStream.forEach(requesters::remove);

                    //Sends response informing that the stream could not be activated
                    sendActivateRouteResponse(requesters, false);

                    //If a recover route was issued, and the node does not have any valid route, issues a
                    if(recoverRoute) {
                        sendRecoverRouteFrame(neighboursWantingStream);
                        clearAllRequestVariables();
                        recoverRoute = false;
                        return true;
                    }

                    clearAllRequestVariables();
                }else{
                    if(neighbourName != null) requesters.add(neighbourName);
                    contactedNodes = reunionOfContactedNodes;

                    newRoute = routingTable.getBestRoute(contactedNodes);
                    String newProvName = newRoute.snd;

                    //Adds the name of the node to the route that the request travelled
                    Set<String> newContacted = new HashSet<>(contactedNodes);
                    newContacted.add(nodeName);

                    //Requests the neighbour (next node in the new route) to activate it
                    try { sendActivateRouteRequest(newProvName, routingTable.containsRoutes(newContacted), arReqFrame.updateRouteMode, newContacted); }
                    catch (Exception e){
                        //If an Exception occurred, there is some problem with the connection with the provider
                        // this node is trying to contact. So the routes of that node are removed.
                        routingTable.removeRoutes(newProvName);
                        //Tries to activate a new route, when requesting to activate route fails
                        logger.info("Activating the new route failed. Will try again.");
                        activateRouteInCourse = false; //Necessary so a new iteration of activateBestRoute can happen
                        activateRoute(new ActivateRouteRequestFrame(true));
                    }
                }

                return true;
            }else {
                //If an activation is in course, sets the flag to true, so the routing handler
                // is forced to wait for a response to proceed
                waitForResponse = true;
                return false;
            }
        }
    }

    /**
     * @param neighbourName   Name of the neighbour that should receive the request
     * @param noMoreRoutes    If the node does not have more routes other than the one he is sending the request to
     * @param updateRouteMode Bool indicating if the active route request is ment to trigger a attempt of route update through the whole route
     * @param contacted       Nodes that already have been contacted to ask a route
     * @throws Exception If there is no connetion handler for the given neighbour,
     *                   or if there was a problem sending the frame.
     */
    private void sendActivateRouteRequest(String neighbourName, boolean noMoreRoutes, boolean updateRouteMode, Set<String> contacted) throws Exception{
        lastNodeRequested = neighbourName;
        TaggedConnection tc = neighbourTable.getConnectionHandler(neighbourName).getTaggedConnection();
        tc.send(new ActivateRouteRequestFrame(neighbourName, noMoreRoutes, updateRouteMode, contacted).serialize());
        logger.info("Sent activate route request to " + neighbourName + ". Contacted nodes: " + contacted);
    }

    /**
     * Handles the activate route request received from a neighbour imediatelly
     * after an activate route request has been sent to the same neighbour.
     * @return if null, then the activateRoute should not return.
     * Otherwise, should return the given value.
     */
    private Boolean handleSameNodeParallelSentReceivedRequest(String neighbourName,
                                                              boolean noMoreRoutes,
                                                              Set<String> reunionOfContactedNodes,
                                                              ActivateRouteRequestFrame arReqFrame){
        //Supposed to handle a problem between two nodes, a 'null' neighbour is not valid
        if(neighbourName == null) return null;

        //If a request was sent to neighbour X and a response was received from neighbour X "simultaneously"
        if(lastNodeRequested != null && lastNodeRequested.equals(neighbourName)){

            if(noMoreRoutes){
                //If the neighbour does not have any additional routes that do not use this node as provider
                if(arReqFrame.noMoreRoutes) {
                    //Send negative answer because the activation of a route cannot be performed
                    sendActivateRouteResponse(List.of(neighbourName), false);
                    //Removes neighbour routes
                    routingTable.removeRoutes(neighbourName);
                    clearAllRequestVariables();
                    //TODO - recovery mode
                }
                // [ELSE] the neighbour has additional routes then this node lets it handle the activation of the route
                return true;
            }
            //If this node has additional routes
            else {
                //If the neighbour does not have any additional routes that do not use this node as provider
                if(arReqFrame.noMoreRoutes) {
                    //Since this node has additional routes, it will be the one handling the activation of a new route
                    // and the neighbour is added to the nodes interested in knowing if
                    // this node was able to activate a route
                    requesters.add(neighbourName);
                    //to avoid the activation of a incompatible route //TODO - N deve funcionar em todos os casos (se n der vai ser preciso fazer uma especie de stack(com o requester e os contacted nodes dele))
                                                                      //TODO - como já foi feita a determinação de quem tem de iniciar, pode - se mandar o frame de volta para a queue, "usar um self activate"
                                                                      // e ativar a flag "waitForResponse"
                    contactedNodes = reunionOfContactedNodes;
                    //unlocks the activation of a new route
                    activateRouteInCourse = false;
                }
                else {
                    //Since both have additional routes, any of them should be able to handle the request.
                    // Calculates a value that allows to choose the one that should handle it.
                    int compareTo = timestampLastARR.compareTo(arReqFrame.timestamp);
                    if(compareTo == 0) compareTo = nodeName.compareTo(neighbourName);

                    //if the neighbour sent the request first
                    if(compareTo > 0){
                        //this node will be the one handling the request, so the neighbour is added to the nodes interested in knowing if
                        // this node was able to activate a route
                        requesters.add(neighbourName);
                        //to avoid the activation of a incompatible route //TODO - N deve funcionar em todos os casos (ver TODO de cima)
                        contactedNodes = reunionOfContactedNodes;
                        //unlocks the activation of a new route
                        activateRouteInCourse = false;
                    }
                    //If this node sent the request first
                    else if (compareTo < 0) {
                        //Discards the frame, since a response should come from the neighbour
                        return true;
                    }
                    // (compareTo == 0) <=> impossible if the nodes have unique names
                    else {
                        sendActivateRouteResponse(List.of(neighbourName), false);
                        return true;
                    }
                }
            }
        }

        return null;
    }

    /* ********* Activate Route Response ********* */

    private void sendActivateRouteResponse(Collection<String> targets, boolean response){
        logger.info("Sending activate route response (" + response + ") to " + targets);
        for(String target : targets){
            try {
                TaggedConnection tc = neighbourTable.getConnectionHandler(target).getTaggedConnection();
                tc.send(new ActivateRouteResponseFrame(target,response).serialize());
                neighbourTable.updateWantsStream(target, response);
            }catch (Exception ignored){
                logger.warning("Could not send activate route response to " + target);
                //If an Exception occurred, there is some problem with the connection with the provider
                // this node is trying to contact. So the routes of that node are removed.
                routingTable.removeRoutes(target);
            }
        }
    }

    private void handleActivateRouteResponse(ActivateRouteResponseFrame arRespFrame){
        String neighbourName = arRespFrame.neighbourName;
        boolean response = arRespFrame.response;

        if(!neighbourName.equals(lastNodeRequested)){
            logger.warning("Received an unexpected activate route response from " + neighbourName);
            return;
        }else {
            waitForResponse = false; //If the expected response was received, unlocks otherFramesQueue

            logger.info("Received activate route response (" + response + ") from " + neighbourName);

            //If while waiting for a response the node itself became a server
            if(clientTable.hasServers()){
                //If the response was positive, then the route was activated
                // and since it is no longer needed, a deactivate route frame is sent
                if(response) sendDeactivateRoute(neighbourName);
                routingTable.deactivateRoute(neighbourName);
                sendActivateRouteResponse(requesters, response);
                clearAllRequestVariables();
            }else {
                //If the response received was true, then the route can be activated
                if (response) {
                    //Deactivates the current route before activating the new one
                    deactivateRoute(true, newRoute != null ? newRoute.snd : null);
                    boolean success = routingTable.activateRoute(newRoute);

                    if (success) {
                        sendActivateRouteResponse(requesters, true);
                        clearAllRequestVariables();
                    } else {
                        //This else case should not happen, since the provider should send a recover route.
                        if (newRoute == null)
                            logger.warning("Could not activate route. New route is null.");
                        else
                            logger.warning("Could not activate route to server " + newRoute.fst + " using " + newRoute.snd + "as provider.");
                    }
                } else {
                    routingTable.removeRoutes(neighbourName);
                    activateRouteInCourse = false;
                    activateRoute(new ActivateRouteRequestFrame(true));
                }
            }
        }
    }

    /* ********* Recover Route ********* */

    private void sendRecoverRouteFrame(Collection<String> targets) {
        logger.info("Sending recover route frame to " + targets);
        for (String target : targets) {
            try {
                TaggedConnection taggedConnection = neighbourTable.getConnectionHandler(target).getTaggedConnection();
                taggedConnection.send(new RecoverRouteFrame(target).serialize());
                neighbourTable.updateWantsStream(target, false);
                logger.info("Sent recover route frame to " + target);
            } catch (Exception ignored) {
                logger.warning("Could not send recover route frame to " + target);
                //If an Exception occurred, there is some problem with the connection with the provider
                // this node is trying to contact. So the routes of that node are removed.
                routingTable.removeRoutes(target);
            }
        }
    }

    //TODO

    /**
     * Recover Route should be called when a connection with a neighbour was lost.
     * This method is responsible to restore the normal state of the node.
     * @param rrf Recover route frame. Contains the name of the neighbour node that "died",
     *            or that could not recover a route to the server.
     */
    private void handleRecoverRoute(RecoverRouteFrame rrf){
        String neighbourName = rrf.neighbourName;
        //-> verificar se queria a stream, se queria, remove lo
        //-> se era provider, remover rotas e ativar nova rota
        //-> ter cuidado com o q acontece dos dois lados da stream

        //If node wanted the stream, then marks it as someone who does not want the stream anymore,
        // and deactivates the route if he was the only one wanting the stream
        if(neighbourTable.wantsStream(neighbourName)){
            logger.info("Marking " + neighbourName + " as someone who does not want the stream.");
            neighbourTable.updateWantsStream(neighbourName, false);
            deactivateRoute(true, null);
        }

        //If the node was the provider, then removes its routes, and
        // tries to activate a new route.
        //TODO - known issue - wont be able to recover route, if the only route available is through a neighbour wanting the stream.
        Tuple<String,String> activeRoute = routingTable.getActiveRoute();
        if(activeRoute != null && activeRoute.snd.equals(neighbourName)){
            logger.info(neighbourName + " was a provider. Its routes will be deleted, and an attempt to discover a new route will be performed.");
            routingTable.removeRoutes(neighbourName);
            activateRouteInCourse = false;
            activateRoute(new ActivateRouteRequestFrame(true));
        }
    }

    /* ********* Deactivate Route ********* */

    private void sendDeactivateRoute(String provider) {
        logger.info("Sending deactivate route frame to " + provider);
        try {
            TaggedConnection taggedConnection = neighbourTable.getConnectionHandler(provider).getTaggedConnection();
            taggedConnection.send(new DeactivateRouteFrame(provider).serialize());
        } catch (Exception ignored) {
            logger.warning("Could not send deactivate route frame to " + provider);
            //If an Exception occurred, there is some problem with the connection with the provider
            // this node is trying to contact. So the routes of that node are removed.
            routingTable.removeRoutes(provider);
        }
    }

    /**
     * If there is a route active, then deactivates it. A combination of selfRequest == true e newProvider == null, forces the deactivation of the route.
     * @param selfRequest If the request was made my the node itself. True means forcing the deactivation of the route.
     * @param newProvider New provider of the stream. If the new provider of the stream is the same as the active one, the stream wont be deactivated.
     */
    private void deactivateRoute(boolean selfRequest, String newProvider) {
        Tuple<String, String> activeRoute = routingTable.getActiveRoute();
        if (activeRoute != null && (selfRequest || (!clientTable.hasClients()
                && !neighbourTable.anyNeighbourWantsTheStream()))
                && !activeRoute.snd.equals(newProvider)) {
            routingTable.deactivateRoute(activeRoute.snd);
            sendDeactivateRoute(activeRoute.snd);
            logger.info("Route from provider " + activeRoute.snd + " deactivated!");
        } else {
            logger.info("Route, " + activeRoute + ", was not deactivated.");
        }
    }

    /**
     * Deactivates the route if the neighbour that sent the frame, was the only one wanting the stream.
     * @param drf Deactivate route frame
     */
    private void handleDeactivateRoute(DeactivateRouteFrame drf){
        String neighbourName = drf.neighbourName;

        //Neighbour requested the deactivation, therefore it no longer wants the stream
        if (neighbourName != null){
            if(neighbourTable.wantsStream(neighbourName)) {
                logger.info(neighbourName + " no longer wants the stream.");
                neighbourTable.updateWantsStream(neighbourName, false);
            }
            else logger.warning("Received unexpected request to deactivate route from " + neighbourName);
        }

        //Requests the provider to deactivate the route,
        // if the node does not have any clients attached and
        // if there are no neighbours wanting the stream
        deactivateRoute(false, null);
    }

    /* ********* Clear variables ********** */
    private void clearLastRequestVariables(){
        lastNodeRequested = null;
        timestampLastARR = null;
    }

    private void clearActivateRouteRequestVariables(){
        requesters.clear();
        contactedNodes.clear();
        activateRouteInCourse = false;
    }

    private void clearAllRequestVariables(){
        clearLastRequestVariables();
        clearActivateRouteRequestVariables();
    }

    /* ******* Calls from the outside ******* */

    /**
     * Converts the information given into a specialized frame.
     * @param neighbourName Node that sent the frame.
     * @param frame Frame
     */
    public void pushRoutingFrame(String neighbourName, Frame frame){
        switch (frame.getTag()) {
            case Tags.ACTIVATE_ROUTE -> {
                insertIntoOtherFramesQueue(ActivateRouteRequestFrame.deserialize(neighbourName, frame));
                logger.info("Received activate route request frame from " + neighbourName + ". Frame : " + frame);
            }
            case Tags.DEACTIVATE_ROUTE -> {
                logger.info("Received deactivate route frame from " + neighbourName + ". Frame : " + frame);
                insertIntoOtherFramesQueue(DeactivateRouteFrame.deserialize(neighbourName, frame));
            }
            case Tags.RESPONSE_ACTIVATE_ROUTE -> {
                logger.info("Received activate route response frame from " + neighbourName + ". Frame : " + frame);
                insertIntoResponseFramesQueue(ActivateRouteResponseFrame.deserialize(neighbourName, frame));
            }
            case Tags.RECOVER_ROUTE -> {
                logger.info("Received recover route frame from " + neighbourName + ". Frame : " + frame);
                insertIntoResponseFramesQueue(RecoverRouteFrame.deserialize(neighbourName, frame));
            }
        }
    }
}
