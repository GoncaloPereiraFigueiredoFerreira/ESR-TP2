package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Utilities.ProtectedQueue;
import speedNode.Utilities.Serialize;
import speedNode.Nodes.Tables.*;
import speedNode.Utilities.Tags;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.Tuple;


public class ControlWorker implements Runnable{
    //Bootstrap info
    private final String bootstrapIP;
    private final int bootstrapPort = 12345;
    private int timeToWaitForBootstrap = 5 * 60 * 1000;
    private TaggedConnection bootstrapConnection;

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
    private final Map<String, ConnectionHandler> connectionsMap = new HashMap<>();
    private final ReentrantLock connectionsLock = new ReentrantLock();

    // Queue with frames to be handled by the main control thread
    private final ProtectedQueue<Tuple<String,Frame>> framesInputQueue = new ProtectedQueue<>();

    // To keep track of the threads that need to join the main thread when terminating the program
    private final Set<Thread> threads = new HashSet<>();

    // To keep track of the neighbours that were sent the flood msg
    //Servidores diferentes
    private Set<Tuple<String,String>> floodSent = new HashSet<>();
    private Set<Tuple<String,String>> floodReceived = new HashSet<>();

    //Child threads will use this exception as stop flag
    private Exception exception = null;

    // true when the node is for flood operations
    private boolean ready = false;

    // true when there is, at least one route to a server
    private boolean readyToActivateRoutes = false;

    public ControlWorker(String bindAddress, String bootstrapIP, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.bindAddress = bindAddress;
        this.neighbourTable = neighbourTable;
        this.routingTable = routingTable;
        this.clientTable = clientTable;
        this.bootstrapIP = bootstrapIP;
    }

    @Override
    public void run() {
        try{
            ss = new ServerSocket(ssPort, 0, InetAddress.getByName(bindAddress));
            connectToBootstrap();
            requestNeighbours();

            //Runs the thread responsible for accepting neighbours' connections
            Thread acceptConnectionsThread = new Thread(() -> {
                try { attendNewConnections(); }
                catch (IOException e) { exception = e; }});
            threads.add(acceptConnectionsThread);
            acceptConnectionsThread.start();

            connectToNeighbours();
            try {
                //Waits until the necessary conditions are fulfilled
                checkStartConditions(); //TODO - alterar o sleep q esta funcao tem
            } catch (InterruptedException ignored) {}

            informReadyStateToBootstrap();
            ready = true;

            while(exception != null){
                Tuple<String,Frame> tuple = framesInputQueue.popElem();
                handleFrame(tuple.fst, tuple.snd);
            }

        }catch (IOException ioe){
            //TODO - handle exception
            ioe.printStackTrace();
        }


        //Closing process
        try { bootstrapConnection.close(); }
        catch (IOException ignored) {}
        for(Thread t : threads) {
            try { t.join();}
            catch (InterruptedException ignored) {}
        }
    }

    /* ****** Connect To Bootstrap ****** */
    private void connectToBootstrap() throws IOException {
        //Connect to bootstrap
        Socket s = new Socket(bootstrapIP, bootstrapPort);
        s.setSoTimeout(timeToWaitForBootstrap);
        bootstrapConnection = new TaggedConnection(s);
    }

    /* ****** Request Neighbours ****** */

    /**
     * Asks bootstrap for the neighbours
     * @throws IOException if there is any problem with the socket or if the frame received was not the one expected.
     */
    private void requestNeighbours() throws IOException {
        //Get neighbours from bootstrap
        bootstrapConnection.send(0, Tags.REQUEST_NEIGHBOUR_CONNECTION, new byte[]{});
        Frame neighboursFrame = bootstrapConnection.receive();
        if(neighboursFrame.getTag() != Tags.RESPONSE_NEIGHBOUR_CONNECTION)
            throw new IOException("Frame with tag" + Tags.RESPONSE_NEIGHBOUR_CONNECTION + "expected!");
        List<String> ips = Serialize.deserializeListOfStrings(neighboursFrame.getData());

        //Initial fill of neighbours' table
        this.neighbourTable.addNeighbours(ips);
    }


    /* ****** Connect to Neighbours ****** */

    private void connectToNeighbours() throws IOException {
        var neighbours = neighbourTable.getNeighbours();
        //Filters the neighbours that are already connected
        neighbours = neighbours.stream()
                .filter(neighbour -> !connectionsMap.containsKey(neighbour) )
                .collect(Collectors.toList());

        for(String neighbour : neighbours)
            connectToNeighbour(neighbour);
    }

    private void connectToNeighbour(String neighbour) throws IOException{
        try {
            Socket s = new Socket(neighbour, ssPort);
            s.setSoTimeout(timeToWaitForNeighbour); //Sets the waiting time till the connection is established, after which the connection is dropped
            TaggedConnection tc = new TaggedConnection(s);
            tc.send(0, Tags.REQUEST_NEIGHBOUR_CONNECTION, new byte[]{}); //Send connection request
            Frame frame = tc.receive(); //Waits for answer

            //If the tag is not equal to RESPONSE_NEIGHBOUR_CONNECTION then the connection is refused
            if(frame.getTag() != Tags.RESPONSE_NEIGHBOUR_CONNECTION){
                s.close();
            }

            //Boolean == True -> Neighbour added the connection to his map
            //Otherwise -> Neighbour did not add connection to his map
            if(Serialize.deserializeBoolean(frame.getData())){
                try{
                    connectionsLock.lock();
                    //Only adds the connection if there isn't already a connection
                    //Or in case there is, if the neighbours IP is lexically inferior
                    ConnectionHandler ch = connectionsMap.get(neighbour);
                    if(ch == null)
                        initiateNeighbourConnectionReceiver(neighbour, tc);
                    else if(neighbour.compareTo(bindAddress) < 0) {
                        ch.close();
                        initiateNeighbourConnectionReceiver(neighbour, tc);
                    }
                    else s.close();
                }finally{connectionsLock.unlock();}
            }
            else s.close();
        }
        catch (UnknownHostException ignored){}
    }


    /* ****** Attend New Connections ****** */

    private void attendNewConnections() throws IOException {
        Set<Thread> childThreads = new HashSet<>();

        //TODO - acrescentar condicao de encerramento gracioso
        while (exception == null) {
            //Removes threads that finished
            childThreads.removeIf(t -> !t.isAlive());

            Socket s = ss.accept();
            s.setSoTimeout(timeToWaitForNeighbour);
            TaggedConnection tc = new TaggedConnection(s);
            String contact = s.getInetAddress().getHostAddress();

            Thread t = new Thread(() -> {
                try {
                    Frame frame = tc.receive();
                    switch (frame.getTag()) {
                        case Tags.REQUEST_NEIGHBOUR_CONNECTION -> acceptNeighbourConnection(contact, tc);
                        case Tags.CONNECT_AS_CLIENT_EXCHANGE -> acceptNewClient(contact, tc);
                        case Tags.CONNECT_AS_SERVER_EXCHANGE -> acceptNewServer(contact, tc);
                    }
                } catch (IOException ignored) {}

                try { tc.close(); }
                catch (IOException ignored){}
            });
            t.start();
            childThreads.add(t);
        }
    }

    private void acceptNeighbourConnection(String neighbour, TaggedConnection tc) throws IOException{

        //if the connectionMap already contains the ip of the Socket, an answer claiming the existence of the ip is sent
        try {
            connectionsLock.lock();
            ConnectionHandler ch = connectionsMap.get(neighbour);
            if(ch != null){
                var b = Serialize.serializeBoolean(false);
                tc.send(0,Tags.RESPONSE_NEIGHBOUR_CONNECTION,b);
                tc.close();
            }
            //otherwise, an answer saying that the ip was added is sent
            else{
                var b = Serialize.serializeBoolean(true);
                tc.send(0,Tags.RESPONSE_NEIGHBOUR_CONNECTION,b);
                initiateNeighbourConnectionReceiver(neighbour, tc);
            }
        }
        finally {
            connectionsLock.unlock();
        }

    }

    //TODO - acabar depois de fazer flood
    private void acceptNewClient(String client, TaggedConnection tc) throws IOException {
        this.clientTable.addNewClient(client);
        tc.send(0, Tags.CONNECT_AS_CLIENT_EXCHANGE, new byte[]{});

        while(!readyToActivateRoutes){
            try {
                //TODO - talvez substituir sleep por await de uma condition
                Thread.sleep(200);
            } catch (InterruptedException e) {
                System.out.println("Interrupted (activate best route)");
                return;
            }
        }

        activateBestRoute();
        //true -> adicionar á tabela dos clientes
        //pedir para criar rotas ate servidores
    }

    //TODO - acabar depois de fazer flood
    private void acceptNewServer(String server, TaggedConnection tc){
        //true -> adicionar à tabela dos servidores
        //informar bootstrap q é servidor
        try{
            //Acknowledge server
            tc.send(0, Tags.CONNECT_AS_SERVER_EXCHANGE, new byte[]{});

            //Request permission to flood
            bootstrapConnection.send(0, Tags.FLOOD_PERMISSION_EXCHANGE, new byte[]{});

            //Receives response from bootstrap
            Frame frame = bootstrapConnection.receive();

            //Checks the tag
            if(frame.getTag() == Tags.FLOOD_PERMISSION_EXCHANGE){
                boolean answer = Serialize.deserializeBoolean(frame.getData());
                if(answer) {
                    tc.send(0, Tags.REQUEST_STREAM, new byte[]{});

                    while (!ready){
                        try {
                            //TODO - talvez substituir sleep por await de uma condition
                            Thread.sleep(200);
                        } catch (InterruptedException e) {
                            System.out.println("Interrupted (flood)");
                            return;
                        }
                    }

                    this.clientTable.addNewServer(server);
                    startFlood(server);
                }
                else {
                    // If the answer is not affirmative, cancels the stream
                    tc.send(0, Tags.CANCEL_STREAM, new byte[]{});
                }
            }
            else {
                //If the tag does not match "FLOOD_PERMISSION_EXCHANGE", then cancels the stream
                tc.send(0, Tags.CANCEL_STREAM, new byte[]{});
                return;
            }

        }catch (IOException ignored){}
    }

    /* ****** Initiate Neighbour Connections Receivers ****** */

    private void initiateNeighbourConnectionReceiver(String neighbour, TaggedConnection tc){
        ConnectionHandler ch = new ConnectionHandler(neighbour, tc);
        try {
            connectionsLock.lock();
            if(connectionsMap.containsKey(neighbour)) return;
            connectionsMap.put(neighbour, ch);
            neighbourTable.updateActiveState(neighbour,true);
        }finally { connectionsLock.unlock(); }

        Thread t = new Thread(ch);
        t.start();
        threads.add(t);
    }

    class ConnectionHandler implements Runnable {
        private final String neighbour;
        private TaggedConnection connection;
        private boolean keepRunning = true;

        public ConnectionHandler(String neighbour, TaggedConnection connection) {
            this.connection = connection;
            this.neighbour = neighbour;
        }

        public void run() {
            while (keepRunning && exception == null) { //TODO - acrescentar condicao de encerramento gracioso
                Frame frame = null;

                //TODO - Verifications can be done here
                try { frame = connection.receive(); }
                catch (IOException ignored) {}

                //Inserts frame in queue
                if (frame != null)
                    framesInputQueue.pushElem(new Tuple<>(neighbour, frame));
            }
        }
        //TODO- Eliminar rotas quando a conexão termina

        public TaggedConnection getTaggedConnection() { return connection; }
        public void setTaggedConnection(TaggedConnection connection) { this.connection = connection; }
        public boolean isKeepRunning() { return keepRunning; }
        public void setKeepRunning(boolean keepRunning) { this.keepRunning = keepRunning; }


        public void close() {
            keepRunning = false;
            try { connection.close();}
            catch (Exception ignored){}
        }
    }


    /* ****** Check Necessary Conditions to Start ****** */

    private void checkStartConditions() throws InterruptedException {
        boolean allReady = false;
        var neighbours = neighbourTable.getNeighbours();

        while (!allReady){
            allReady = true;

            for(int i = 0; i < neighbours.size() && allReady; i++)
                if(!neighbourTable.isActive(neighbours.get(i)))
                    allReady = false;

            //TODO - substituir por metodo da NeighboursTable que aguarda que todos os neighbours estejam prontos
            Thread.sleep(200);
        }
    }

    /* ****** Inform bootstrap that the node is ready ****** */

    private void informReadyStateToBootstrap() throws IOException {
        //Informs bootstrap that the node is ready
        bootstrapConnection.send(0, Tags.INFORM_READY_STATE, new byte[]{});
    }


    /* ****** Activate best route ****** */

    //TODO - acabar activateBestRoute
    private void activateBestRoute() {
        routingTable.activateBestRoute();
        //Se a rota mudou
        //  -> Contactar novo vizinho e cancelar rota antiga (se existir)
        //Else
        //  -> nao fazer nada
    }


    /* ****** Flood ****** */

    /* *** PAYLOAD ***
        Composed by:
            -> server that triggered the flood
            -> number of jumps between overlay nodes
            -> timestamp of the start of the flood

     */

    //TODO - fazer flood
    private void startFlood(String server) throws IOException{
        for(ConnectionHandler ch : connectionsMap.values()){
            TaggedConnection tc = ch.getTaggedConnection();

            // Construct payload
            List<String> msg_flood = new ArrayList<>();
            msg_flood.add(server); // Server
            msg_flood.add("0");  // nr of jumps
            String time = Long.toString(System.currentTimeMillis());
            msg_flood.add(time); // timestamp of the start of the flood

            tc.send(0,Tags.FLOOD,Serialize.serializeListOfStrings(msg_flood));
        }
    }



    /* ****** Handle Frames Received ****** */
    private void handleFrame(String ip, Frame frame) {
        if(frame == null)
            return;
        try {
            switch (frame.getTag()){
                case Tags.FLOOD -> handleFloodFrame(ip,frame);
                //case Tags.ACTIVATE_ROUTE -> handleActivateRoute(frame);
                //case Tags.DEACTIVATE_ROUTE -> handleDeactivateRoute(frame);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    private void handleFloodFrame(String ip,Frame frame) throws IOException{
        var previous_msg = Serialize.deserializeListOfStrings(frame.getData());

        //inserting a server path to Routing Table
        String ServerIp=previous_msg.get(0);
        int Jumps = Integer.parseInt(previous_msg.get(1)) + 1;
        float Time = System.currentTimeMillis()-Long.parseLong(previous_msg.get(2));
        routingTable.addServerPath(ServerIp,ip,Jumps,Time,false);
        floodReceived.add(new Tuple<>(ServerIp,ip));

        for(ConnectionHandler ch : connectionsMap.values()){

            if( !floodReceived.contains(new Tuple<>(ServerIp,ch.neighbour))
             && !floodSent.contains(new Tuple<>(ServerIp,ch.neighbour))){

                TaggedConnection tc = ch.getTaggedConnection();

                List<String> msg_flood = new ArrayList<>();
                msg_flood.add(ServerIp);
                msg_flood.add(Integer.toString(Jumps));
                msg_flood.add(previous_msg.get(2));

                tc.send(0,Tags.FLOOD,Serialize.serializeListOfStrings(msg_flood));
                floodSent.add(new Tuple<>(ServerIp,ch.neighbour));

            }
        }
    }
}
