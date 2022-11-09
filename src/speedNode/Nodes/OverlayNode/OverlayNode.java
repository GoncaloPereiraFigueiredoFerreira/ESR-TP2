package speedNode.Nodes.OverlayNode;

import speedNode.Nodes.Serialize;
import speedNode.Nodes.Tables.ClientTable;
import speedNode.Nodes.Tables.NeighbourTable;
import speedNode.Nodes.Tables.RoutingTable;
import speedNode.TaggedConnection.Frame;
import speedNode.TaggedConnection.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class OverlayNode implements Runnable{
    private Socket s;
    private final int bootstrapPort = 12345;
    private final String bootstrapIP;
    TaggedConnection tc;
    private final boolean server;

    private NeighbourTable neighbourTable;
    private RoutingTable routingTable;
    private ClientTable clientTable;

    public OverlayNode(String bootstrapIP,boolean server,boolean client) {
        this.server= server;
        this.bootstrapIP = bootstrapIP;
        this.client= client;
    }

    private void initial_tables(List<String> ips){
        this.neighbourTable.addNeighbours(ips);

    }

    public void run(){

        byte[] b = {};
        try {
            this.s = new Socket(this.bootstrapIP, this.bootstrapPort);
            tc = new TaggedConnection(this.s);

            tc.send(0,1,b); //Send request with tag 1
            Frame frame = tc.receive();
            List<String> ips = Serialize.deserializeListOfStrings(frame.getData());
            System.out.println(ips); //TODO - remover print
            initial_tables(ips);
            ControlWorker control_node= new ControlWorker(ips,this.server,this.neighbourTable,this.routingTable,this.clientTable);
            control_node.run();


        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void launch(List<String> args) {
        if (args == null || args.size() == 0) {
            System.out.println("No arguments were given!");
            return;
        }
        OverlayNode node = new OverlayNode(args.get(0),args.get(1),args.get(2));
        node.run();
    }
}
