package speedNode.Nodes.OverlayNode;

import speedNode.Nodes.Serialize;
import speedNode.Nodes.Tables.*;
import speedNode.TaggedConnection.TaggedConnection;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import speedNode.TaggedConnection.Frame;


public class ControlWorker implements Runnable{
    private final Boolean server;
    private List<String> ips;

    private ServerSocket ss;
    private TaggedConnection tc;
    private AtomicBoolean closeServer = new AtomicBoolean(false);
    private String bootstrapIP;
    private final int bootstrapPort = 12345;

    private INeighbourTable neighbourTable;
    private IRoutingTable routingTable;
    private IClientTable clientTable;




    public ControlWorker(String bootstrapIP, boolean server, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.server=server;
        this.neighbourTable= neighbourTable;
        this.routingTable = routingTable;
        this.clientTable= clientTable;
        this.bootstrapIP=bootstrapIP;

    }

    /**
     * regista o valor da lista de ips dos vizinhos
     * @throws IOException
     */

    private void lista_ips() throws IOException {
        byte[] b = {};
        Socket bootstrapSocket = new Socket(this.bootstrapIP, this.bootstrapPort);
        tc = new TaggedConnection(bootstrapSocket);
        tc.send(0,1,b); //Send request with tag 1
        Frame frame = tc.receive();
        List<String> ips = Serialize.deserializeListOfStrings(frame.getData());
        System.out.println(ips); //TODO - remover print
        initial_tables(ips);
        this.ips=ips;
    }

    private void initial_tables(List<String> ips){
        this.neighbourTable.addNeighbours(ips);
    }

    /**
     *  a mensagem que envia é composta por [ipServidor,ipNodo,nºsaltos,tempo]
     *  ver tags do taggedConnection
     */

    private void init_flood() throws IOException {
        List<String> msg_flood_init = new ArrayList<>();

        //TODO- ver se ServerSocket.getInetAddress().getHostAddress() retorna o ip da maquina em que esta
        //ip servidor
        msg_flood_init.add(ss.getInetAddress().getHostAddress());
        System.out.println("nao deve dar local host: "+ss.getInetAddress().getHostAddress());//TODO-tirar print
        //ip nodo atual
        msg_flood_init.add(ss.getInetAddress().getHostAddress());
        //nº saltos
        msg_flood_init.add("0");
        //tempo
        String time= ""+System.currentTimeMillis();
        msg_flood_init.add(time);

        for(String ip : ips){
            Socket s = new Socket(ip,3000);
            TaggedConnection tc = new TaggedConnection(s);
            tc.send(0,3,Serialize.serializeListOfStrings(msg_flood_init));
        }
    }

    /**
     *
     * @param Socket do nodo em que esta
     * @param previous_msg mensagem enviada pelo vizinho
     * @return mensagem para enviar aos vizinhos
     */
    private List<String> floodMsg(Socket cliente,List<String> previous_msg){
        String Serverip=previous_msg.get(0);
        //TODO-ver se o ip é assim que se descobre
        String Ip= cliente.getInetAddress().getHostAddress();
        String Jumps = Integer.toString(Integer.parseInt(previous_msg.get(2))+1);
        String tempo=previous_msg.get(3);

        List<String> msg_flood = new ArrayList<>();

        msg_flood.add(Serverip);
        msg_flood.add(Ip);
        msg_flood.add(Jumps);
        msg_flood.add(tempo);

        return msg_flood;

    }

    /**
     *
     * @param cliente
     * @param previous_msg
     */
    private void makeTables(Socket cliente,List<String> previous_msg){
        String Serverip=previous_msg.get(0);
        String vizinhoIp= cliente.getInetAddress().getHostAddress();
        int Jumps = Integer.parseInt(previous_msg.get(2));
        float Time = System.currentTimeMillis()-Long.parseLong(previous_msg.get(3));
        this.routingTable.addServerPath(Serverip,vizinhoIp,Jumps,Time,false);
    }

    private void flood(Socket cliente,List<String> data) throws IOException {
        floodMsg(cliente,data);

        //TODO-Fazer as tabelas
        makeTables(cliente,data);

        List<String> msg_flood = new ArrayList<>();



        //tempo


        for(String ip : ips){
            Socket s = new Socket(ip,3000);
            TaggedConnection tc = new TaggedConnection(s);
            //[se é ou nao servidor-neste caso é sempre servidor, nº saltos - neste caso é 0,tempo em milisegundos desde que]
            tc.send(0,1,Serialize.serializeListOfStrings(msg_flood));
        }

    }

    public void run(){
        try {

            lista_ips();

            int serverPort = 3000;
            this.ss = new ServerSocket(serverPort);
            if(server){init_flood();}

            while(!closeServer.get()){
                Socket s = this.ss.accept();
                ConnectionHandler handler = new ConnectionHandler(s);
                new Thread(handler).start();

            }
            ss.close();
            closeServer.set(true);
        } catch (IOException e) {
            e.printStackTrace();
        }

    }


    class ConnectionHandler implements Runnable{
        private final Socket client;
        private TaggedConnection tc;

        public ConnectionHandler(Socket client){
            this.client= client;
        }

        public void run(){
            try {
                this.tc = new TaggedConnection(this.client);
                Frame frame = tc.receive();
                int tag = frame.getTag();
                if(tag==3){flood(client,Serialize.deserializeListOfStrings(frame.getData()));}

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }




}
