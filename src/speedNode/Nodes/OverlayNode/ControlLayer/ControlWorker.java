package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Nodes.OverlayNode.TransmissionLayer.TransmissionWorker;
import speedNode.Utilities.*;
import speedNode.Nodes.OverlayNode.Tables.*;
import speedNode.Utilities.Logs.MyLogger;
import speedNode.Utilities.TaggedConnection.Serialize;
import speedNode.Utilities.TaggedConnection.TaggedConnection;

import java.io.EOFException;
import java.io.IOException;
import java.net.*;
import java.util.*;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.Tags;

//TODO - verificar como Ã© a situacao de um nodo ser servidor e cliente
public class ControlWorker implements Runnable{
    //Logger
    private MyLogger logger;

    //Bootstrap info
    private final String bootstrapIP;
    private final int bootstrapPort = 12345;
    private int timeToWaitForBootstrap = 5 * 60 * 1000; //Waiting time for a packet after the connection is established, after which the connection should be dropped

    //Self info
    private String nodeName;
    private final int ssPort = 54321;
    private ServerSocket ss;
    private int timeToWaitForClient = 5 * 60 * 1000; //Waiting time for a packet after the connection is established, after which the connection should be dropped

    //Tables
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;

    // Queue with frames to be handled by the main control thread
    // Tuple : (ip of who sent the frame, frame)
    private final ProtectedQueue<Tuple<String,Frame>> framesInputQueue = new ProtectedQueue<>();

    // To keep track of the threads that need to join the main thread when terminating the program
    private final Set<Thread> threads = new HashSet<>();

    // To keep track of the neighbours that were sent the flood msg
    private final FloodControl floodControl = new FloodControl();

    private RoutingHandler routingHandler;

    //Child threads will use this exception as stop flag
    private Exception exception = null;

    //Ready state
    private final BoolWithLockCond startPermission = new BoolWithLockCond(false);

    public ControlWorker(String bootstrapIP, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.neighbourTable = neighbourTable;
        this.routingTable = routingTable;
        this.clientTable = clientTable;
        this.bootstrapIP = bootstrapIP;
    }

    @Override
    public void run() {
        try{
            //Creates logger
            this.logger = MyLogger.createLogger(UUID.randomUUID().toString(), "---","", false);
            logger.info("Running...");

            //Starts the server socket, which is necessary to establish connections with neighbours
            ss = new ServerSocket(ssPort);
            logger.info("Server Socket created.");
            startThreadToAttendNewConnections();
            requestNeighboursAndConnect();

            //Starts transmission thread
            TransmissionWorker transmissionWorker = new TransmissionWorker(neighbourTable, routingTable, clientTable);
            Thread transWorker = new Thread(transmissionWorker);
            transWorker.start();
            threads.add(transWorker);

            startRoutingHandler();
            boolean permissionToStart = informReadyStateToBootstrapAndWaitForStartPermission();

            System.out.println("************************\nconnected neighbours: " + neighbourTable.getConnectedNeighbours()+"\n**********************");

            if(permissionToStart) {
                while (exception == null) {
                    //Remove dead threads
                    threads.removeIf(t -> !t.isAlive());

                    //Handle received frame
                    Tuple<String, Frame> tuple = framesInputQueue.popElem();
                    handleFrame(tuple.fst, tuple.snd);
                }
            }
        }catch (Exception e){
            //TODO - handle exception
            e.printStackTrace();
            //System.out.println("Escaxou com: " + e.getMessage());
        }

        //Closing process
        close();
    }

    private void startRoutingHandler() {
        routingHandler = new RoutingHandler(nodeName, neighbourTable, routingTable, clientTable, logger);
        Thread t = new Thread(routingHandler);
        threads.add(t);
        t.start();
    }

    /* ****** Connect To Bootstrap ****** */
    private TaggedConnection connectToBootstrap() throws IOException {
        logger.info("Connecting to bootstrap...");

        //Connect to bootstrap
        System.out.println("Bootstrap ip: " + bootstrapIP);
        Socket s = new Socket(bootstrapIP, bootstrapPort);
        s.setSoTimeout(timeToWaitForBootstrap); //Sets the waiting time for a packet after the connection is established, after which the connection is dropped
        TaggedConnection tc = new TaggedConnection(s);

        logger.info("Connected to bootstrap.");
        return tc;
    }

    /* ****** Request Neighbours ****** */

    /**
     * Asks bootstrap for the neighbours
     * @throws IOException if there is any problem with the socket or if the frame received was not the one expected.
     */
    private void requestNeighboursAndConnect() throws IOException {
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

        //Triple for all the neighbours (neighbourName, local IP, neighbour IP)
        List<String> responseList = Serialize.deserializeListOfStrings(neighboursFrame.getData());
        nodeName = responseList.remove(0); // First element of the list corresponds to the name of the node
        logger.changeLogNameAndDisplayName(nodeName, nodeName);
        logger.info("Bootstrap response to neighbours request: " + responseList);

        //Initial fill of neighbours' table
        for(int i = 0 ; i < responseList.size(); i += 3){
            //Tries to connect to the neighbour if he doesnt already exist
            try {
                neighbourTable.writeLock();
                if (neighbourTable.addNeighbour(responseList.get(i),
                        responseList.get(i + 1),
                        responseList.get(i + 2)))
                    connectToNeighbour(responseList.get(i));
            }finally { neighbourTable.writeUnlock(); }
        }


        //this.neighbourTable.addNeighbours(responseList);
        //logger.info(responseList + " added to Neighbours' Table.");

        bootstrapConnection.close();
    }


    /* ****** Connect to Neighbours ****** */

    private void connectToNeighbour(String neighbourName) throws IOException{
        String localIP = neighbourTable.getLocalIP(neighbourName);
        String neighbourIP = neighbourTable.getNeighbourIP(neighbourName);
        if(localIP == null || neighbourIP == null) return;

        Socket s = null;
        try {
            //Creating socket to contact neighbour
            logger.info("Creating socket to contact neighbour " + neighbourName + ". (Local IP: " + localIP + " | Neighbour IP: " + neighbourIP + ")");
            s = new Socket(neighbourIP, ssPort, InetAddress.getByName(localIP), 0);
            s.setSoTimeout(timeToWaitForClient); //Sets the waiting time till the connection is established, after which the connection is dropped
            TaggedConnection tc = new TaggedConnection(s);
            logger.info("Created socket to contact neighbour " + neighbourName + ".");

            //Requesting neighbour to establish connection
            logger.info("Requesting neighbour " + neighbourName + " to establish connection.");
            tc.send(0, Tags.REQUEST_NEIGHBOUR_CONNECTION, Serialize.serializeString(nodeName)); //Send connection request
            Frame frame = tc.receive(); //Waits for answer

            //If the tag is not equal to RESPONSE_NEIGHBOUR_CONNECTION then the connection is refused
            if(frame.getTag() != Tags.RESPONSE_NEIGHBOUR_CONNECTION){
                logger.info("Expected frame with tag" + Tags.RESPONSE_NEIGHBOUR_CONNECTION + " from neighbour " + neighbourName);
                s.close();
                return;
            }

            //Boolean == True -> Neighbour added the connection to his map
            //Otherwise -> Neighbour did not add connection to his map
            if(Serialize.deserializeBoolean(frame.getData())){
                logger.info("Neighbour " + neighbourName + " accepted connection.");

                try{
                    neighbourTable.writeLock();

                    //Only adds the connection if there isn't already a connection
                    //Or, in case there is one active, if the neighbours IP is lexically superior
                    ConnectionHandler ch = neighbourTable.getConnectionHandler(neighbourName);
                    if(ch == null || neighbourIP.compareTo(localIP) > 0)
                        initiateNeighbourConnectionReceiver(neighbourName, tc);
                    else {
                        s.close();
                        return;
                    }
                }finally{neighbourTable.writeUnlock();}
            }
            else s.close();
        }
        catch (UnknownHostException ignored){
            logger.warning("Unknown Host Exception for neighbour " + neighbourName);
        }
        catch (ConnectException ce){
            logger.warning("Could not connect to neighbour " + neighbourName);
        }
        catch (EOFException eofe){
            logger.warning("EOF Exception thrown when trying to read response from " + neighbourName);
            if(s != null && !s.isClosed()) s.close();
        }
    }

    private void connectToUnconnectedNeighbours() throws IOException {
        //Get neighbours that are not connected
        var neighbours = neighbourTable.getUnconnectedNeighbours();

        logger.info("Connecting to neighbours: " + neighbours);

        for(String neighbour : neighbours)
            connectToNeighbour(neighbour);
    }

    /*
    private void connectToNeighbourAntigo(String neighbour) throws IOException{
        Socket s = null;
        try {
            //Creating socket to contact neighbour
            logger.info("Creating socket to contact neighbour " + neighbour + "...");
            s = new Socket(neighbour, ssPort);
            s.setSoTimeout(timeToWaitForClient); //Sets the waiting time till the connection is established, after which the connection is dropped
            TaggedConnection tc = new TaggedConnection(s);
            logger.info("Created socket to contact neighbour " + neighbour + ".");

            //Requesting neighbour to establish connection
            logger.info("Requesting neighbour " + neighbour + " to establish connection.");
            logger.info("Sending the interfaces ---------------> " + this.ipv4Interfaces);
            tc.send(0, Tags.REQUEST_NEIGHBOUR_CONNECTION, Serialize.serializeListOfStrings(this.ipv4Interfaces)); //Send connection request //TODO: Adicionar a lista dos ips
            Frame frame = tc.receive(); //Waits for answer
            logger.info("Received answer from neighbour " + neighbour);

            //If the tag is not equal to RESPONSE_NEIGHBOUR_CONNECTION then the connection is refused
            if(frame.getTag() != Tags.RESPONSE_NEIGHBOUR_CONNECTION){
                logger.info("Expected frame with tag" + Tags.RESPONSE_NEIGHBOUR_CONNECTION + " from neighbour " + neighbour);
                s.close();
                return;
            }
            List<String> interfaceIpsNeigh;
            //Boolean == True -> Neighbour added the connection to his map
            //Otherwise -> Neighbour did not add connection to his map
            if((interfaceIpsNeigh = Serialize.deserializeListOfStrings(frame.getData())).size()>0){
                logger.info("Neighbour " + neighbour + " accepted connection.");

                try{
                    neighbourTable.writeLock();

                    String interfaceIP = chooseInterface(interfaceIpsNeigh);
                    System.out.println("\n\n ----->MINHA INTERFACE: " + tc.getSocket().getLocalAddress().getHostAddress() + " | NEIGHBOUR: " + neighbour +
                            " | INTERFACE NEIGHBOUR: " + interfaceIP + "\n\n\n" );
                    neighbourTable.setNeighbourIP(neighbour,interfaceIP);

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
    */


    //private String chooseInterface(List<String> neighbourInterfaces){
    //    String interfaceIP = "";
    //    for (String neighbourIP :neighbourInterfaces){
    //        for (String localIP : this.ipv4Interfaces){
    //            try {
    //                byte[] tempNIP = Arrays.copyOfRange( InetAddress.getByName(neighbourIP).getAddress(),0,3);
    //                byte[] tempLIP = Arrays.copyOfRange( InetAddress.getByName(localIP).getAddress(),0,3);
    //                if (Arrays.equals(tempNIP,tempLIP)) {
    //                    interfaceIP = neighbourIP;
    //                    break;
    //                }
    //            } catch (UnknownHostException e) {
    //                e.printStackTrace();
    //            }
    //        }
    //        if (!Objects.equals(interfaceIP, "")) break;
    //    }
    //    return interfaceIP;
    //}

    /*
    private Tuple<String,String> chooseInterface(List<String> neighbourInterfaces){
        String interfaceIP = null;
        for (String neighbourIP :neighbourInterfaces){
            for (String localIP : this.ipv4Interfaces){
                try {
                    byte[] tempNIP = Arrays.copyOfRange( InetAddress.getByName(neighbourIP).getAddress(),0,3);
                    byte[] tempLIP = Arrays.copyOfRange( InetAddress.getByName(localIP).getAddress(),0,3);
                    if (Arrays.equals(tempNIP,tempLIP)) {
                        return new Tuple<>(localIP, neighbourIP);
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }*/



    /**
     * Returns collection of ipv4 addresses associated with the devices interfaces. Loopback excluded.
     * @return collection of ipv4 addresses associated with the devices interfaces. Loopback excluded.
     * @throws SocketException
     */
    public static Collection<String> getNetworkInterfacesIPv4s() throws SocketException{
        Collection<String> ipv4s = new HashSet<>();
        Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
        for (NetworkInterface netint : Collections.list(nets)) {
            Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
            for(InetAddress inetAddress : Collections.list(inetAddresses)){
                if(inetAddress instanceof Inet4Address){
                    String ipv4 = inetAddress.getHostAddress();
                    if(!ipv4.equals("127.0.0.1"))
                        ipv4s.add(ipv4);
                }
            }
        }
        return ipv4s;
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

            try {
                String contactIP = s.getInetAddress().getHostAddress();
                logger.info("New connection from " + contactIP);
                s.setSoTimeout(timeToWaitForClient);
                TaggedConnection tc = new TaggedConnection(s);

                Thread t = new Thread(() -> {
                    try {
                        Frame frame = tc.receive();
                        System.out.println("Received frame from " + contactIP + " with tag " + frame.getTag()); //TODO - tirar print

                        switch (frame.getTag()) {
                            case Tags.REQUEST_NEIGHBOUR_CONNECTION -> acceptNeighbourConnection(tc, frame);
                            case Tags.CONNECT_AS_CLIENT_EXCHANGE -> acceptNewClient(contactIP, tc);
                            case Tags.CONNECT_AS_SERVER_EXCHANGE -> acceptNewServer(contactIP, tc);
                            case Tags.CLIENT_CLOSE_CONNECTION -> closeClientConnection(contactIP);
                        }
                    } catch (IOException ignored) {
                        logger.warning("IO Exception while handling frame from " + contactIP);
                        try {
                            tc.close();
                        } catch (IOException ignored2) {
                        }
                    }
                });
                t.start();
                childThreads.add(t);
                logger.info("Created thread to attend the request from " + contactIP);

            }catch (IOException ignored){}
        }

        logger.info("New connections attendant: joining child threads...");
        for(Thread t : childThreads) {
            try { t.join();}
            catch (InterruptedException ignored) {}
        }
        logger.info("Connection attendant closed.");
    }

    private void acceptNeighbourConnection(TaggedConnection tc, Frame frame) throws IOException{
        try {
            neighbourTable.writeLock();
            String neighbourName = Serialize.deserializeString(frame.getData());

            //if a connection is already active, an answer is sent rejecting the new connection and
            // claiming the existence of a connection with the neighbour
            if(neighbourTable.isConnected(neighbourName)){
                logger.info("Rejecting " + neighbourName + "'s connection... Another connection is already active!");

                //Sends response rejecting the new connection
                var b = Serialize.serializeBoolean(false);
                tc.send(0,Tags.RESPONSE_NEIGHBOUR_CONNECTION,b);

                //Closes socket
                tc.close();
            }
            //otherwise, an answer saying that the ip was added is sent
            else{
                logger.info("Accepting " + neighbourName + "'s connection...");

                //Sends response accepting the new connection
                var b = Serialize.serializeBoolean(true);
                tc.send(0,Tags.RESPONSE_NEIGHBOUR_CONNECTION,b);

                neighbourTable.addNeighbour(neighbourName, tc.getLocalIP(), tc.getHostIP());
                initiateNeighbourConnectionReceiver(neighbourName, tc);
            }
        }
        finally { neighbourTable.writeUnlock(); }
    }

    private void closeClientConnection(String contact) {
        clientTable.removeClient(contact);
        routingHandler.pushRoutingFrame(null, new Frame(0, Tags.DEACTIVATE_ROUTE, new byte[]{}));
    }

    private String identifyNeighbour(List<String> ipv4InterfacesNeighbour) {
        for(String neighbour : neighbourTable.getNeighbours())
            if(ipv4InterfacesNeighbour.contains(neighbour))
                return neighbour;
        return null;
    }

    private void acceptNewClient(String client, TaggedConnection tc) throws IOException {
        activateBestRoute();

        System.out.println("Melhor rota ativa!!");
        Tuple<String, String> route = routingTable.getActiveRoute();
        if(route != null)
            System.out.println("Melhor rota: " +  route.fst +  "  " + route.snd);
        else
            System.out.println("Rota é nula!");

        if(routingTable == null || routingTable.getActiveRoute() == null){
            tc.send(0, Tags.CANCEL_STREAM, new byte[]{});
            return;
        }

        //Sends frame informing the acceptance of the client //TODO - verificar palavra-passe para seguranca
        tc.send(0, Tags.CONNECT_AS_CLIENT_EXCHANGE, new byte[]{});

        //Adds client to clients' table
        this.clientTable.addNewClient(client);
    }

    private void acceptNewServer(String server, TaggedConnection tc){
        try{
           // TaggedConnection bootstrapConnection = connectToBootstrap();

            //Acknowledge server
            tc.send(0, Tags.CONNECT_AS_SERVER_EXCHANGE, new byte[]{});

            /*


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
            */

            try{
                //Awaits for the permission to start
                startPermission.awaitForValue(true);

                //Registers server in clients table
                this.clientTable.addNewServer(server);

                //Starts a flood to inform the rest of the overlay's nodes of the existence of the new server
                startFlood(server);

                //Requests the stream (maybe it shouldn't be requested until, at least, a client requests it)
                tc.send(0, Tags.REQUEST_STREAM, new byte[]{});
            }catch (InterruptedException ie){
                //Cancels the stream
                tc.send(0, Tags.CANCEL_STREAM, new byte[]{});
            }
        }catch (IOException ignored){}
    }

    /* ****** Initiate Neighbour Connections Receivers ****** */

    private void initiateNeighbourConnectionReceiver(String neighbourName, TaggedConnection tc){
        ConnectionHandler ch;
        try {
            neighbourTable.writeLock();

            //Creates a connection handler
            ch = new ConnectionHandler(neighbourName, tc, framesInputQueue, logger);

            //Inserts the new connection handler in neighbours table.
            //If the table had a connection handler for the given neighbour,
            // then a receiver must be already active and should be closed.
            ConnectionHandler previousCh = neighbourTable.updateConnectionHandler(neighbourName, ch);

            //closes the existing connection if there is one
            if(previousCh != null) {
                logger.info("Closing existing connection for neighbour " + neighbourName);
                previousCh.close();
            }

            //Starts the thread responsible for receiving the frames from the neighbour
            Thread t = new Thread(ch);
            t.start();
            threads.add(t);

            logger.info("Connection receiver initiated for neighbour " + neighbourName);
        }finally { neighbourTable.writeUnlock(); }
    }

    /* ****** Check Necessary Conditions to Start ****** */

    //TODO - Recovery mode -> Tentar adquirir pelo menos uma rota
    //Waits until the necessary conditions are fulfilled
    private void waitsForStartConditionsToBeMet() throws InterruptedException {
        boolean allReady = false;
        var neighbours = neighbourTable.getNeighbours();
        System.out.println("********** waitForStart neighbours: " + neighbours + "************");


        int j = 0;
        String notCon = null;

        while (!allReady){
            allReady = true;

            for(int i = 0; i < neighbours.size() && allReady; i++)
                if(!neighbourTable.isConnected(neighbours.get(i))) {
                    allReady = false;
                    notCon = neighbours.get(i);
                }

            //TODO - substituir por metodo da NeighboursTable que aguarda que todos os neighbours estejam prontos
            if(!allReady) {
                System.out.println(j + " - Waiting for " + notCon + " | ch: " + neighbourTable.getConnectionHandler(notCon));
                Thread.sleep(2000);
            }
        }

        System.out.println("********** all ready ************");
    }

    /* ****** Inform bootstrap that the node is ready ****** */

    //private void informReadyStateToBootstrap() throws IOException {
    //    //Informs bootstrap that the node is ready
    //    TaggedConnection bootstrapConnection = connectToBootstrap();
    //    bootstrapConnection.send(0, Tags.INFORM_READY_STATE, new byte[]{});
    //    bootstrapConnection.close();
    //    logger.info("Informed bootstrap of ready state.");
    //}

    private boolean informReadyStateToBootstrapAndWaitForStartPermission() throws IOException {
        //Informs bootstrap that the node is ready
        TaggedConnection bootstrapConnection = connectToBootstrap();
        logger.info("Informed bootstrap of ready state.");
        bootstrapConnection.send(0, Tags.INFORM_READY_STATE, new byte[]{});
        Frame frame = bootstrapConnection.receive();

        boolean permission;
        if(frame.getTag() == Tags.START_PERMISSION){
            permission = Serialize.deserializeBoolean(frame.getData());
        }else {
            logger.warning("Expected frame with start permission.");
            throw new IOException("Expected frame with start permission.");
        }

        startPermission.setAndSignalAll(true);
        bootstrapConnection.close();

        if(permission)
            logger.info("Permission to start granted by bootstrap.");
        else
            logger.warning("Permission to start not granted by bootstrap.");

        return permission;
    }



    /* ****** Activate best route ****** */

    private void activateBestRoute(){
        if(routingHandler == null)
            return;

        Frame frame = new Frame(0, Tags.ACTIVATE_ROUTE, new byte[]{});
        routingHandler.pushRoutingFrame(null, frame);

        routingHandler.waitForRouteUpdate();
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
        List<String> route = new ArrayList<>();
        route.add(nodeName);
        sendFloodFrame(neighbours, // list of neighbours that should receive the flood
                       floodControl.getNextFloodIndex(server), //flood identification
                       new FloodControl.FloodInfo(server, 0, System.nanoTime(), route));
    }

    private void handleFloodFrame(String neighbourName, Frame frame) throws IOException{
        //Deserialize frame data
        FloodControl.FloodInfo floodInfo = FloodControl.FloodInfo.deserialize(frame.getData());

        //Ignores own flood frame
        if(clientTable.containsServer(floodInfo.server) || floodInfo.route.contains(nodeName)) return;

        //Registering the node that sent the frame, in order to avoid spreading the flood back to the node it came from
        int floodIndex = frame.getNumber();
        boolean validFlood = floodControl.validateFlood(floodInfo.server, floodIndex);

        if(validFlood) {
            //Get nodes that already got the flood frame
            var floodedNodes = floodControl.floodedNodes(floodInfo.server);

            //Get all neighbours and removes the ones that already got flooded
            List<String> neighbours = neighbourTable.getNeighbours();
            neighbours.removeAll(floodedNodes);
            neighbours.remove(neighbourName);

            //includes the node itself in the route
            floodInfo.route.add(nodeName);

            //increments number of jumps
            floodInfo.jumps++;

            //Sends the flood frame to every node that has not been flooded and is active
            sendFloodFrame(neighbours, // list of neighbours that should receive the flood
                           floodIndex, //flood identification
                           floodInfo); //timestamp

            //inserting/updating a server path to Routing Table
            if(!routingTable.existsInRoutingTable(floodInfo.server, neighbourName))
                routingTable.addServerPath(floodInfo.server, neighbourName, floodInfo.jumps, System.nanoTime() - floodInfo.timestamp, false);
            else
                routingTable.updateMetrics(floodInfo.server, neighbourName, floodInfo.jumps, System.nanoTime() - floodInfo.timestamp);

            routingTable.printTables(); //TODO - remover aqui
        }
    }

    /**
     * Sends flood frame to every neighbour present in the given list.
     * @param neighbours List of neighbours that should receive the flood frame
     * @param floodIndex Index of the flood
     * @param floodInfo Flood info
     */
    private void sendFloodFrame(List<String> neighbours, int floodIndex, FloodControl.FloodInfo floodInfo){
        for (String neighbour : neighbours) {
            try {
                ConnectionHandler ch = neighbourTable.getConnectionHandler(neighbour);
                System.out.println("Connection HANDLER de " + neighbour + ": " + ch);

                //Executes if the connection is active
                if (ch != null && ch.isRunning()) {
                    TaggedConnection tc = ch.getTaggedConnection();

                    //Creates the frame data
                    byte[] data = floodInfo.serialize();

                    //Sends the frame to the neighbour
                    tc.send(floodIndex, Tags.FLOOD, data);

                    //Registers that the neighbour has received the flood frame to avoid repeting the send operation
                    floodControl.sentFlood(floodInfo.server, neighbour, floodIndex);

                    logger.info("Sent flood frame from server " + floodInfo.server + " with index " + floodIndex + " to " + neighbour);
                }else
                    logger.info(neighbour + " inactive. Did not send flood frame from server " + floodInfo.server);

            }catch (IOException ignored){
                //Ignores the Exception that happened for a specific neighbour in order to not disrupt
                // the process for the rest of the nodes
                logger.warning("IO Exception when sending flood frame from server " + floodInfo.server + " to " + neighbour);
            }
        }
    }

    /* ****** Handle Frames Received ****** */
    private void handleFrame(String neighbourName, Frame frame) {
        if(frame == null)
            return;
        try {
            switch (frame.getTag()){
                case Tags.FLOOD -> handleFloodFrame(neighbourName, frame);
                default -> routingHandler.pushRoutingFrame(neighbourName, frame);
            }

        }catch (Exception e){
            e.printStackTrace();
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

            if(ch != null && ch.isRunning())
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
