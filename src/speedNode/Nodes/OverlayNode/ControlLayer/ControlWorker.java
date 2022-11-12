package speedNode.Nodes.OverlayNode.ControlLayer;

import speedNode.Nodes.ProtectedQueue;
import speedNode.Nodes.Serialize;
import speedNode.Nodes.Tables.*;
import speedNode.TaggedConnection.TaggedConnection;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import speedNode.TaggedConnection.Frame;
import speedNode.Utils.Tuple;


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
    Exception exception = null;
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
            //Waits until the necessary conditions are fulfilled
            try {checkStartConditions();} //TODO - alterar o sleep q esta funcao tem
            catch (InterruptedException ignored) {}
            informReadyStateToBootstrap();


            //try {
            //    this.tc = new TaggedConnection(this.client);
            //    Frame frame = tc.receive();
            //    int tag = frame.getTag();
            //    if(tag==5){flood(client,Serialize.deserializeListOfStrings(frame.getData()));}
            //
            //} catch (IOException e) {
            //    e.printStackTrace();
            //}

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
        TaggedConnection connection = new TaggedConnection(s);
    }

    /* ****** Request Neighbours ****** */

    /**
     * Asks bootstrap for the neighbours
     * @throws IOException if there is any problem with the socket or if the frame received was not the one expected.
     */
    private void requestNeighbours() throws IOException {
        //Get neighbours from bootstrap
        bootstrapConnection.send(0, 1, new byte[]{});
        Frame neighboursFrame = bootstrapConnection.receive();
        if(neighboursFrame.getTag() != 2)
            throw new IOException("Frame with tag 2 expected!");
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
            Frame frame = tc.receive();

            //If the tag is not 2 than the connection is refused
            if(frame.getTag() != 2){
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
                        case 2 -> acceptNeighbourConnection(contact, tc);
                        case 3 -> acceptNewClient(contact, tc);
                        case 4 -> acceptNewServer(contact, tc);
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
                ch.getTaggedConnection().send(0,2,b);
                tc.close();
            }
            //otherwise, an answer saying that the ip was added is sent
            else{
                var b = Serialize.serializeBoolean(true);
                tc.send(0,2,b);
                initiateNeighbourConnectionReceiver(neighbour, tc);
            }
        }
        finally {
            connectionsLock.unlock();
        }

    }

    //TODO - acabar depois de fazer flood
    private void acceptNewClient(String client, TaggedConnection tc){
        //true -> adicionar á tabela dos clientes
        //pedir para criar rotas ate servidores
        this.clientTable.addNewClient(client);
    }

    //TODO - acabar depois de fazer flood
    private void acceptNewServer(String server, TaggedConnection tc){
        //true -> adicionar á tabela dos servidores
        //informar bootstrap q é servidor
        this.clientTable.addNewServer(server);
        try{
            bootstrapConnection.send(0, 4,new byte[]{});
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
                try {
                    frame = connection.receive();
                } catch (IOException ignored) {
                }

                //Inserts frame in queue
                if (frame != null)
                    framesInputQueue.pushElem(new Tuple<>(neighbour, frame));
            }
        }

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
        bootstrapConnection.send(0, 5, new byte[]{});
    }

    /**
     * regista o valor da lista de ips dos vizinhos
     * @throws IOException
     */

    //private void lista_ips() throws IOException {
    //    byte[] b = {};
    //    Socket bootstrapSocket = new Socket(this.bootstrapIP, this.bootstrapPort);
    //    tc = new TaggedConnection(bootstrapSocket);
    //    tc.send(0,1,b); //Send request with tag 1
    //    Frame frame = tc.receive();
    //    List<String> ips = Serialize.deserializeListOfStrings(frame.getData());
    //    System.out.println(ips); //TODO - remover print
    //    initial_tables(ips);
    //    this.ips=ips;
    //}
//
    //private void initial_tables(List<String> ips){
    //    this.neighbourTable.addNeighbours(ips);
    //}
//
    ///**
    // *  a mensagem que envia é composta por [ipServidor,ipNodo,nºsaltos,tempo]
    // *  ver tags do taggedConnection
    // */
//
    //private void init_flood() throws IOException {
    //    List<String> msg_flood_init = new ArrayList<>();
//
    //    //TODO- ver se ServerSocket.getInetAddress().getHostAddress() retorna o ip da maquina em que esta
    //    //ip servidor
    //    msg_flood_init.add(ss.getInetAddress().getHostAddress());
    //    System.out.println("nao deve dar local host: "+ss.getInetAddress().getHostAddress());//TODO-tirar print
    //    //ip nodo atual
    //    msg_flood_init.add(ss.getInetAddress().getHostAddress());
    //    //nº saltos
    //    msg_flood_init.add("0");
    //    //tempo
    //    String time= ""+System.currentTimeMillis();
    //    msg_flood_init.add(time);
//
    //    for(String ip : ips){
    //        Socket s = new Socket(ip,3000);
    //        TaggedConnection tc = new TaggedConnection(s);
    //        tc.send(0,3,Serialize.serializeListOfStrings(msg_flood_init));
    //    }
    //}
//
    ///**
    // *
    // * @param previous_msg mensagem enviada pelo vizinho
    // * @return mensagem para enviar aos vizinhos
    // */
    //private List<String> floodMsg(Socket cliente,List<String> previous_msg){
    //    String Serverip=previous_msg.get(0);
    //    //TODO-ver se o ip é assim que se descobre
    //    String Ip= cliente.getInetAddress().getHostAddress();
    //    String Jumps = Integer.toString(Integer.parseInt(previous_msg.get(2))+1);
    //    String tempo=previous_msg.get(3);
//
    //    List<String> msg_flood = new ArrayList<>();
//
    //    msg_flood.add(Serverip);
    //    msg_flood.add(Ip);
    //    msg_flood.add(Jumps);
    //    msg_flood.add(tempo);
//
    //    return msg_flood;
//
    //}
//
    ///**
    // *
    // * @param cliente
    // * @param previous_msg
    // */
    //private void makeTables(Socket cliente,List<String> previous_msg){
    //    String Serverip=previous_msg.get(0);
    //    String vizinhoIp= cliente.getInetAddress().getHostAddress();
    //    int Jumps = Integer.parseInt(previous_msg.get(2));
    //    float Time = System.currentTimeMillis()-Long.parseLong(previous_msg.get(3));
    //    this.routingTable.addServerPath(Serverip,vizinhoIp,Jumps,Time,false);
    //}
//
    //private void flood(Socket cliente,List<String> data) throws IOException {
    //    floodMsg(cliente,data);
//
    //    //TODO-Fazer as tabelas
    //    makeTables(cliente,data);
//
    //    List<String> msg_flood = new ArrayList<>();
//
//
//
    //    //tempo
//
//
    //    for(String ip : ips){
    //        Socket s = new Socket(ip,3000);
    //        TaggedConnection tc = new TaggedConnection(s);
    //        //[se é ou nao servidor-neste caso é sempre servidor, nº saltos - neste caso é 0,tempo em milisegundos desde que]
    //        tc.send(0,1,Serialize.serializeListOfStrings(msg_flood));
    //    }
//
    //}
//
//
    //class ConnectionHandler implements Runnable{
    //    private final Socket client;
    //    private TaggedConnection tc;
//
    //    public ConnectionHandler(Socket client){
    //        this.client= client;
    //    }
//
    //    public void run(){
    //        try {
    //            this.tc = new TaggedConnection(this.client);
    //            Frame frame = tc.receive();
    //            int tag = frame.getTag();
    //            if(tag==3){flood(client,Serialize.deserializeListOfStrings(frame.getData()));}
//
    //        } catch (IOException e) {
    //            e.printStackTrace();
    //        }
    //    }
//
    //}




}