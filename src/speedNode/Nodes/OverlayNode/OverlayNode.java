package speedNode.Nodes.OverlayNode;

import speedNode.Nodes.OverlayNode.ControlLayer.ControlWorker;
import speedNode.Nodes.OverlayNode.TransmissionLayer.TransmitionWorker;
import speedNode.Nodes.OverlayNode.Tables.*;

import java.util.List;

public class OverlayNode{
    /*
    private final String bindAddress;
    private final String bootstrapIP;
    TaggedConnection tc;
    private final boolean server;


    private INeighbourTable neighbourTable;
    private IRoutingTable routingTable;
    private IClientTable clientTable;

    public OverlayNode(String bindAddress, String bootstrapIP,boolean server,boolean client) {
        this.bindAddress = bindAddress;
        this.server= server;
        this.bootstrapIP = bootstrapIP;
        this.neighbourTable= new NeighbourTable();
        this.routingTable= new RoutingTable();
        this.clientTable= new ClientTable();
    }


    public void run(){


        ControlWorker control_node= new ControlWorker(this.bindAddress, this.bootstrapIP, this.neighbourTable, this.routingTable, this.clientTable);
        control_node.run();


    }*/

    public static void launch(List<String> args) {
        if (args == null || args.size() == 0) {
            System.out.println("OverlayNode: No arguments were given! Requires the IP of the preferred device's interface, and the bootstrap's interface IP.");
            return;
        }


        INeighbourTable neighbourTable = new NeighbourTable();
        IRoutingTable routingTable = new RoutingTable();
        IClientTable clientTable = new ClientTable();


        ControlWorker controlWorker = new ControlWorker(args.get(0), args.get(1), neighbourTable, routingTable, clientTable);
        Thread ctrlWorker = new Thread(controlWorker);
        ctrlWorker.start();


        TransmitionWorker transmitionWorker = new TransmitionWorker(args.get(0),neighbourTable,routingTable,clientTable);
        Thread transWorker = new Thread(transmitionWorker);
        transWorker.start();



    }
}
