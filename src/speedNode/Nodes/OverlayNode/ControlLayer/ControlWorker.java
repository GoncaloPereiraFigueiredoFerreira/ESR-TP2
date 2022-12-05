package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.LoggingToFile;
import speedNode.Utilities.*;
import speedNode.Nodes.Tables.*;
import speedNode.Utilities.TaggedConnection.TaggedConnection;

import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import speedNode.Utilities.TaggedConnection.Frame;

//TODO - verificar como é a situacao de um nodo ser servidor e cliente
public class ControlWorker implements Runnable{
    //Logger
    private final Logger logger;

    //Bootstrap info
    private final String bootstrapIP;
    private final int bootstrapPort = 12345;
    private int timeToWaitForBootstrap = 5 * 60 * 1000;

    //Self info
    private final String bindAddress;
    private final int ssPort = 54321;
    private ServerSocket ss;
    private int timeToWaitForNeighbour = 5 * 60 * 1000;

    //Tables
    private INeighbourTable neighbourTable;
    private IRoutingTable routingTable;
    private IClientTable clientTable;

    // Connections Map
    //private final Map<String, ConnectionHandler> connectionsMap = new HashMap<>();
    //private final ReentrantLock connectionsLock = new ReentrantLock();

    // Queue with frames to be handled by the main control thread
    // Tuple : (ip of who sent the frame, frame)
    private final ProtectedQueue<Tuple<String,Frame>> framesInputQueue = new ProtectedQueue<>();

    // To keep track of the threads that need to join the main thread when terminating the program
    private final Set<Thread> threads = new HashSet<>();

    // To keep track of the neighbours that were sent the flood msg
    private final FloodControl floodControl = new FloodControl();

    //Child threads will use this exception as stop flag
    private Exception exception = null;

    // true when there is, at least one route to a server
    private final BoolWithLockCond readyToActivateRoutes = new BoolWithLockCond(false);

    public ControlWorker(String bindAddress, String bootstrapIP, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.bindAddress = bindAddress;
        this.neighbourTable = neighbourTable;
        this.routingTable = routingTable;
        this.clientTable = clientTable;
        this.bootstrapIP = bootstrapIP;
        this.logger = LoggingToFile.createLogger("CW" + bindAddress + ".txt", "", true);
    }

    @Override
    public void run() {
        try{
            logger.info("Running...");

            ss = new ServerSocket(ssPort, 0, InetAddress.getByName(bindAddress));
            logger.info("Server Socket created.");

            requestNeighbours();
            startThreadToAttendNewConnections();
            connectToNeighbours();

            try { waitsForStartConditionsToBeMet(); }
            catch (InterruptedException ignored) {}

            informReadyStateToBootstrap();

            System.out.println("************************\nconnected neighbours: " + neighbourTable.getConnectedNeighbours()+"\n**********************");

            while(exception != null){
                //Remove dead threads
                threads.removeIf(t -> !t.isAlive());

                //Handle received frame
                Tuple<String,Frame> tuple = framesInputQueue.popElem();
                handleFrame(tuple.fst, tuple.snd);
            }

        }catch (IOException ioe){
            //TODO - handle exception
            ioe.printStackTrace();
        }

        //Closing process
        close();
    }

    /* ****** Custom socket ****** */
    private Socket createSocket(String hostIP, int hostPort) throws IOException {
        return new Socket(hostIP, hostPort, InetAddress.getByName(bindAddress), 0);
    }

    /* ****** Connect To Bootstrap ****** */
    private TaggedConnection connectToBootstrap() throws IOException {
        logger.info("Connecting to bootstrap...");

        //Connect to bootstrap
        Socket s = createSocket(bootstrapIP, bootstrapPort);
        s.setSoTimeout(timeToWaitForBootstrap);
        TaggedConnection tc = new TaggedConnection(s);

        logger.info("Connected to bootstrap.");
        return tc;
    }

    /* ****** Request Neighbours ****** */

    /**
     * Asks bootstrap for the neighbours
     * @throws IOException if there is any problem with the socket or if the frame received was not the one expected.
     */
    private void requestNeighbours() throws IOException {
        TaggedConnection bootstrapConnection = connectToBootstrap();

        //Get neighbours from bootstrap
        logger.info("Requesting neighbours from bootstrap.");
        bootstrapConnection.send(0, Tags.REQUEST_NEIGHBOURS_EXCHANGE, new byte[]{});

        logger.info("Waiting for bootstrap response...");
        Frame neighboursFrame = bootstrapConnection.receive();

        if(neighboursFrame.getTag() != Tags.REQUEST_NEIGHBOURS_EXCHANGE) {
            logger.warning("Received response with wrong tag from bootstrap!");
            throw new IOException("Frame with tag" + Tags.REQUEST_NEIGHBOURS_EXCHANGE + "expected!");
        }

        List<String> ips = Serialize.deserializeListOfStrings(neighboursFrame.getData());
        logger.info("[Bootstrap response] neighbours: " + ips);

        //Initial fill of neighbours' table
        this.neighbourTable.addNeighbours(ips);
        logger.info(ips + " added to Neighbours' Table.");
    }


    /* ****** Connect to Neighbours ****** */

    private void connectToNeighbours() throws IOException {
        //Get neighbours that are not connected
        var neighbours = neighbourTable.getUnconnectedNeighbours();

        logger.info("Connecting to neighbours: " + neighbours);

        for(String neighbour : neighbours)
            connectToNeighbour(neighbour);
    }

    private void connectToNeighbour(String neighbour) throws IOException{
        Socket s = null;
        try {
            //Creating socket to contact neighbour
            logger.info("Creating socket to contact neighbour " + neighbour + "...");
            s = createSocket(neighbour, ssPort);
            s.setSoTimeout(timeToWaitForNeighbour); //Sets the waiting time till the connection is established, after which the connection is dropped
            TaggedConnection tc = new TaggedConnection(s);
            logger.info("Created socket to contact neighbour " + neighbour + ".");

            //Requesting neighbour to establish connection
            logger.info("Requesting neighbour " + neighbour + " to establish connection.");
            tc.send(0, Tags.REQUEST_NEIGHBOUR_CONNECTION, new byte[]{}); //Send connection request
            Frame frame = tc.receive(); //Waits for answer
            logger.info("Received answer from neighbour " + neighbour);

            //If the tag is not equal to RESPONSE_NEIGHBOUR_CONNECTION then the connection is refused
            if(frame.getTag() != Tags.RESPONSE_NEIGHBOUR_CONNECTION){
                logger.info("Expected frame with tag" + Tags.RESPONSE_NEIGHBOUR_CONNECTION + " from neighbour " + neighbour);
                s.close();
                return;
            }

            //Boolean == True -> Neighbour added the connection to his map
            //Otherwise -> Neighbour did not add connection to his map
            if(Serialize.deserializeBoolean(frame.getData())){
                logger.info("Neighbour " + neighbour + " accepted connection.");

                try{
                    neighbourTable.writeLock();

                    //Only adds the connection if there isn't already a connection
                    //Or, in case there is one active, if the neighbours IP is lexically superior
                    ConnectionHandler ch = neighbourTable.getConnectionHandler(neighbour);
                    if(ch == null || neighbour.compareTo(bindAddress) > 0)
                        initiateNeighbourConnectionReceiver(neighbour, tc);
                    else {
                        s.close();
                        return;
                    }
                }finally{neighbourTable.writeUnlock();}
            }
            else s.close();
        }
        catch (UnknownHostException ignored){
            logger.warning("Unknown Host Exception for neighbour " + neighbour);
        }
        catch (ConnectException ce){
            logger.warning("Could not connect to neighbour " + neighbour);
        }
        catch (EOFException eofe){
            logger.warning("EOF Exception thrown when trying to read response from " + neighbour);
            if(s != null && !s.isClosed()) s.close();
        }
    }


    /* ****** Attend New Connections ****** */

    private void startThreadToAttendNewConnections(){
        //Starts the thread responsible for accepting neighbours' connections
        Thread acceptConnectionsThread = new Thread(() -> {
            try { attendNewConnections(); }
            catch (IOException e) { exception = e; }});
        threads.add(acceptConnectionsThread);
        acceptConnectionsThread.start();
        logger.info("Created thread to attend new connections.");
    }

    private void attendNewConnections() throws IOException {
        Set<Thread> childThreads = new HashSet<>();

        //TODO - acrescentar condicao de encerramento gracioso
        while (exception == null) {
            //Removes threads that finished
            childThreads.removeIf(t -> !t.isAlive());

            Socket s = ss.accept();
            String contact = s.getInetAddress().getHostAddress();
            logger.info("New connection from " + contact);
            s.setSoTimeout(timeToWaitForNeighbour);
            TaggedConnection tc = new TaggedConnection(s);

            Thread t = new Thread(() -> {
                try {
                    Frame frame = tc.receive();
                    logger.info("Received frame from " + contact + " with tag " + frame.getTag());

                    switch (frame.getTag()) {
                        case Tags.REQUEST_NEIGHBOUR_CONNECTION -> acceptNeighbourConnection(contact, tc);
                        case Tags.CONNECT_AS_CLIENT_EXCHANGE -> acceptNewClient(contact, tc);
                        case Tags.CONNECT_AS_SERVER_EXCHANGE -> acceptNewServer(contact, tc);
                    }
                } catch (IOException ignored) {
                    logger.warning("IO Exception while handling frame from " + contact);
                    try { tc.close(); }
                    catch (IOException ignored2){}
                }
            });
            t.start();
            childThreads.add(t);
            logger.info("Created thread to attend the request from " + contact);
        }

        logger.info("New connections attendant: joining child threads...");
        for(Thread t : childThreads) {
            try { t.join();}
            catch (InterruptedException ignored) {}
        }
        logger.info("Connection attendant closed.");
    }

    private void acceptNeighbourConnection(String neighbour, TaggedConnection tc) throws IOException{
        try {
            neighbourTable.writeLock();

            //if a connection is already active, an answer is sent rejecting the new connection and
            // claiming the existence of a connection with the neighbour
            if(neighbourTable.isConnected(neighbour)){
                logger.info("Rejecting " + neighbour + "'s connection... Another connection is already active!");

                //Sends response rejecting the new connection
                var b = Serialize.serializeBoolean(false);
                tc.send(0,Tags.RESPONSE_NEIGHBOUR_CONNECTION,b);

                //Closes socket
                tc.close();
            }
            //otherwise, an answer saying that the ip was added is sent
            else{
                logger.info("Accepting " + neighbour + "'s connection...");

                //Sends response accepting the new connection
                var b = Serialize.serializeBoolean(true);
                tc.send(0,Tags.RESPONSE_NEIGHBOUR_CONNECTION,b);

                initiateNeighbourConnectionReceiver(neighbour, tc);
            }
        }
        finally { neighbourTable.writeUnlock(); }
    }

    private void acceptNewClient(String client, TaggedConnection tc) throws IOException {
        //Adds client to clients' table
        this.clientTable.addNewClient(client);
        //Sends frame informing the acceptance of the client //TODO - verificar palavra-passe para seguranca
        tc.send(0, Tags.CONNECT_AS_CLIENT_EXCHANGE, new byte[]{});

        try {
            //Awaits until a valid route is available
            readyToActivateRoutes.awaitForValue(true);
        } catch (InterruptedException e) {
            //If the thread is interrupted, informs the client that it won't be receiving the stream
            tc.send(0, Tags.CANCEL_STREAM, new byte[]{});
            clientTable.removeClient(client);
            return;
        }

        //Activates the best route available
        activateBestRoute();
    }

    private void acceptNewServer(String server, TaggedConnection tc){
        try{
            TaggedConnection bootstrapConnection = connectToBootstrap();

            //Acknowledge server
            tc.send(0, Tags.CONNECT_AS_SERVER_EXCHANGE, new byte[]{});

            //Request bootstrap for the permission to flood
            bootstrapConnection.send(0, Tags.FLOOD_PERMISSION_EXCHANGE, new byte[]{});

            //Receives response from bootstrap
            Frame frame = bootstrapConnection.receive();

            //Checks the tag
            if(frame.getTag() == Tags.FLOOD_PERMISSION_EXCHANGE) {
                //Registers server in clients table
                this.clientTable.addNewServer(server);

                //Starts a flood to inform the rest of the overlay's nodes of the existence of the new server
                startFlood(server);

                //Requests the stream (maybe it shouldn't be requested until, at least, a client requests it)
                tc.send(0, Tags.REQUEST_STREAM, new byte[]{});
                return;
            }else {
                // If the tag does not match "FLOOD_PERMISSION_EXCHANGE"
                // or if the answer is not affirmative, cancels the stream
                tc.send(0, Tags.CANCEL_STREAM, new byte[]{});
            }

            bootstrapConnection.close();
        }catch (IOException ignored){}
    }

    /* ****** Initiate Neighbour Connections Receivers ****** */

    private void initiateNeighbourConnectionReceiver(String neighbour, TaggedConnection tc){
        ConnectionHandler ch;
        try {
            neighbourTable.writeLock();

            //Creates a connection handler
            ch = new ConnectionHandler(neighbour, tc, framesInputQueue);

            //Inserts the new connection handler in neighbours table.
            //If the table had a connection handler for the given neighbour,
            // then a receiver must be already active and should be closed.
            ConnectionHandler previousCh = neighbourTable.updateConnectionHandler(neighbour, ch);

            //closes the existing connection if there is one
            if(previousCh != null) {
                logger.info("Closing existing connection for neighbour " + neighbour);
                previousCh.close();
            }

            //Starts the thread responsible for receiving the frames from the neighbour
            Thread t = new Thread(ch);
            t.start();
            threads.add(t);

            logger.info("Connection receiver initiated for neighbour " + neighbour);
        }finally { neighbourTable.writeUnlock(); }
    }

    /* ****** Check Necessary Conditions to Start ****** */

    //Waits until the necessary conditions are fulfilled
    private void waitsForStartConditionsToBeMet() throws InterruptedException {
        boolean allReady = false;
        var neighbours = neighbourTable.getNeighbours();
        System.out.println("********** waitForStart neighbours: " + neighbours + "************");

        while (!allReady){
            allReady = true;

            for(int i = 0; i < neighbours.size() && allReady; i++)
                if(!neighbourTable.isConnected(neighbours.get(i)))
                    allReady = false;

            //TODO - substituir por metodo da NeighboursTable que aguarda que todos os neighbours estejam prontos
            Thread.sleep(200);
        }

        System.out.println("********** all ready ************");
    }

    /* ****** Inform bootstrap that the node is ready ****** */

    private void informReadyStateToBootstrap() throws IOException {
        //Informs bootstrap that the node is ready
        TaggedConnection bootstrapConnection = connectToBootstrap();
        bootstrapConnection.send(0, Tags.INFORM_READY_STATE, new byte[]{});
        bootstrapConnection.close();
        logger.info("Informed bootstrap of ready state.");
    }


    /* ****** Activate best route ****** */

    //TODO - acabar activateBestRoute
    private void activateBestRoute() throws IOException{

        var oldProvidingIP = routingTable.getActiveRoute().snd;
        routingTable.activateBestRoute();
        var newProvidingIP = routingTable.getActiveRoute().snd;

        ConnectionHandler newProvCH = neighbourTable.getConnectionHandler(newProvidingIP);
        TaggedConnection newProvTC = newProvCH == null ? null : newProvCH.getTaggedConnection();


        if(oldProvidingIP != null && !Objects.equals(oldProvidingIP, newProvidingIP)){
            ConnectionHandler oldProvCH = neighbourTable.getConnectionHandler(oldProvidingIP);
            TaggedConnection oldProvTC = oldProvCH == null ? null : oldProvCH.getTaggedConnection();

            if(newProvTC != null)
                newProvTC.send(0, Tags.ACTIVATE_ROUTE, new byte[]{});

            if(oldProvTC != null)
                oldProvTC.send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
        }
        else if(newProvTC != null)
            newProvTC.send(0, Tags.ACTIVATE_ROUTE, new byte[]{});

        //Se a rota mudou
        //  -> Contactar novo vizinho e cancelar rota antiga (se existir)
        //Else
        //  -> nao fazer nada
    }


    /* ****** Flood ****** */

    /* *** PAYLOAD ***
     *  Composed by:
     *      -> server that triggered the flood
     *      -> number of jumps between overlay nodes
     *      -> timestamp of the start of the flood
     */

    private void startFlood(String server){
        List<String> neighbours = neighbourTable.getNeighbours();

        //Iterates through all neighbours and sends the flood frame for everyone that is active
        for(String neighbour : neighbours){
            ConnectionHandler ch = neighbourTable.getConnectionHandler(neighbour);

            //Executes if the connection is active
            if(ch != null && ch.isRunning()) {
                TaggedConnection tc = ch.getTaggedConnection();

                // Construct payload
                List<String> msg_flood = new ArrayList<>();
                msg_flood.add(server); // Server
                msg_flood.add("0");  // nr of jumps
                String time = Long.toString(System.currentTimeMillis());
                msg_flood.add(time); // timestamp of the start of the flood

                try {
                    tc.send(0, Tags.FLOOD, Serialize.serializeListOfStrings(msg_flood));
                    floodControl.sentFlood(server, tc.getHost(), 0); //Registers the ip of the neighbour to avoid a repetitive send
                } catch (Exception ignored) {}
            }
        }
    }



    /* ****** Handle Frames Received ****** */
    private void handleFrame(String ip, Frame frame) {
        if(frame == null)
            return;
        try {
            switch (frame.getTag()){
                case Tags.FLOOD -> handleFloodFrame(ip,frame);
                case Tags.ACTIVATE_ROUTE -> handleActivateRoute(ip);
                case Tags.DEACTIVATE_ROUTE -> handleDeactivateRoute(ip);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void handleFloodFrame(String ip, Frame frame) throws IOException{
        //Deserialize frame data
        var previous_msg = Serialize.deserializeListOfStrings(frame.getData());
        String serverIp = previous_msg.get(0);
        int Jumps = Integer.parseInt(previous_msg.get(1)) + 1;
        float Time = System.currentTimeMillis() - Long.parseLong(previous_msg.get(2));

        //Registering the node that sent the frame, in order to avoid spreading the flood back to the node it came from
        int floodIndex = frame.getNumber();
        boolean validFlood = floodControl.receivedFlood(serverIp, ip, floodIndex);

        if(validFlood) {
            //inserting a server path to Routing Table
            routingTable.addServerPath(serverIp, ip, Jumps, Time, false);
            //signals all threads waiting to activate a route
            readyToActivateRoutes.setAndSignalAll(true);

            //Get nodes that already got the flood frame
            var floodedNodes = floodControl.floodedNodes(serverIp);


            //Get all neighbours and removes the ones that already got flooded
            List<String> neighbours = neighbourTable.getNeighbours();
            neighbours.removeAll(floodedNodes);

            //Sends the flood frame to every node that has not been flooded and is active
            for (String neighbour : neighbours) {
                ConnectionHandler ch = neighbourTable.getConnectionHandler(neighbour);

                //Executes if the connection is active
                if (ch != null && ch.isRunning()) {
                    TaggedConnection tc = ch.getTaggedConnection();

                    //Creates the frame data
                    List<String> msg_flood = new ArrayList<>();
                    msg_flood.add(serverIp);
                    msg_flood.add(Integer.toString(Jumps));
                    msg_flood.add(previous_msg.get(2));
                    byte[] data = Serialize.serializeListOfStrings(msg_flood);

                    //Sends the frame to the neighbour
                    tc.send(floodIndex, Tags.FLOOD, data);

                    //Registers that the neighbour has received the flood frame
                    floodControl.sentFlood(serverIp, tc.getHost(), floodIndex);
                }
            }
        }
    }

    private void handleActivateRoute(String ip) throws IOException{
        this.neighbourTable.updateWantsStream(ip,true);
        activateBestRoute();
    }

    private void handleDeactivateRoute(String ip) throws IOException{
        this.neighbourTable.updateWantsStream(ip,false);
        if(this.neighbourTable.getNeighboursWantingStream().size() == 0){
            String oldProvidingIP = routingTable.getActiveRoute().snd;
            ConnectionHandler ch = neighbourTable.getConnectionHandler(oldProvidingIP);

            if(ch!=null && ch.isRunning())
                ch.getTaggedConnection().send(0, Tags.DEACTIVATE_ROUTE, new byte[]{});
        }
    }

    /* ****** Close graciously ****** */
    //TODO - Close graciously method
    private void close(){
        try {
            //Interrupts every
            neighbourTable.writeLock();

            //Interrupts every connection receiver
            List<String> connectedNeighbours = neighbourTable.getConnectedNeighbours();
            for (String neighbour : connectedNeighbours)
                neighbourTable.getConnectionHandler(neighbour).close();

            //Interrupts every thread
            interruptAndJoinThreads(this.threads);
        }finally { neighbourTable.writeUnlock(); }
    }

    private static void interruptAndJoinThreads(Collection<Thread> threads){
        //Interrupts threads that are running
        for(Thread t : threads)
            if(t.isAlive() && !t.isInterrupted())
                t.interrupt();

        //Waits for threads to stop running and join them
        for(Thread t : threads) {
            try { t.join();}
            catch (InterruptedException ignored) {}
        }
    }


}
