package speedNode.Nodes.OverlayNode;

import speedNode.Nodes.Serialize;
import speedNode.Nodes.Tables.ClientTable;
import speedNode.Nodes.Tables.NeighbourTable;
import speedNode.Nodes.Tables.RoutingTable;
import speedNode.TaggedConnection.TaggedConnection;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Time;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import speedNode.TaggedConnection.Frame;


public class ControlWorker implements Runnable{
    private final Boolean server;
    private final List<String> ips;
    private ServerSocket ss;
    private AtomicBoolean closeServer = new AtomicBoolean(false);
    private NeighbourTable neighbourTable;
    private RoutingTable routingTable;
    private ClientTable clientTable;
    private ExecutorService pool;

    public ControlWorker(List<String> ips, boolean server, NeighbourTable neighbourTable, RoutingTable routingTable, ClientTable clientTable){
        this.server=server;
        this.ips=ips;
        this.neighbourTable= neighbourTable;
        this.routingTable = routingTable;
        this.clientTable= clientTable;
    }

    /**
     *  a mensagem que envia é composta por [ipServidor,maybe tirar ??? se é servidor,nºsaltos,tempo]
     *  ver tags do taggedConnection
     */

    private void init_flood() throws IOException {
        List<String> msg_flood_init = new ArrayList<>();
        //ip servidor
        msg_flood_init.add(ss.getInetAddress().getHostAddress());
        System.out.println("nao deve dar local host: "+ss.getInetAddress().getHostAddress());//TODO-tirar print
        //se é servidor
        msg_flood_init.add("1");
        //nº saltos
        msg_flood_init.add("0");
        //tempo
        String time= ""+System.currentTimeMillis();
        msg_flood_init.add(time);

        for(String ip : ips){
            Socket s = new Socket(ip,3000);
            TaggedConnection tc = new TaggedConnection(s);
            //[se é ou nao servidor-neste caso é sempre servidor, nº saltos - neste caso é 0,tempo em milisegundos desde que]
            tc.send(0,3,Serialize.serializeListOfStrings(msg_flood_init));
        }
    }


    private void flood(String neighbour,List<String> data) throws IOException {
        this.ips.remove(neighbour);

        String Serverip=data.get(0);
        int Jumps = Integer.parseInt(data.get(2))+1;
        //TODO-tempo ate aop
        float Time = System.currentTimeMillis()-Long.parseLong(data.get(3));
        this.routingTable.addServerPath(Serverip,neighbour,Jumps,Time,false);

        List<String> msg_flood = new ArrayList<>();
        //ip servidor
        msg_flood.add(Serverip);
        //se é servidor
        if(server){
            msg_flood.add("1");
        }
        else{ msg_flood.add("0");}

        //nº saltos
        msg_flood.add(Integer.toString(Jumps));
        //tempo
        msg_flood.add(data.get(3));

        for(String ip : ips){
            Socket s = new Socket(ip,3000);
            TaggedConnection tc = new TaggedConnection(s);
            //[se é ou nao servidor-neste caso é sempre servidor, nº saltos - neste caso é 0,tempo em milisegundos desde que]
            tc.send(0,1,Serialize.serializeListOfStrings(msg_flood));
        }

    }

    public void run(){
        try {
            int serverPort = 3000;
            ss = new ServerSocket(serverPort);

            if(server){init_flood();}
            while(!closeServer.get()){
                Socket s = ss.accept();
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
                if(tag==3){flood(this.client.getInetAddress().getHostAddress(),Serialize.deserializeListOfStrings(frame.getData()));}

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    /*
        public void shutdown(){
            try {
                this.tc.close();
                if (!client.isClosed()) {
                    client.close();
                }

            }catch (IOException e){

            }
        }
    */
    }




}
