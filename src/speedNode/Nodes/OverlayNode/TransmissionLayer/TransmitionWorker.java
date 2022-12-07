package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Utilities.ProtectedQueue;
import speedNode.Nodes.OverlayNode.Tables.IClientTable;
import speedNode.Nodes.OverlayNode.Tables.INeighbourTable;
import speedNode.Nodes.OverlayNode.Tables.IRoutingTable;

import java.net.*;
import java.util.List;

public class TransmitionWorker implements Runnable{
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;
    private final ProtectedQueue<DatagramPacket> inputQueue = new ProtectedQueue<>();
    private final ProtectedQueue<DatagramPacket> outputQueue = new ProtectedQueue<>();
    private DatagramSocket ds;
    private static final int PORT=50000;
    private static final int CLPORT = 25000;
    public static final int MAX_UDP_P_SIZE = 15000; // To be defined

    public TransmitionWorker(INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.clientTable = clientTable;
        this.routingTable = routingTable;
        this.neighbourTable = neighbourTable;
    }

    //TODO: Store all threads in a set, in order to shutdown gracefully

    @Override
    public void run() {

        // Initialization of the communication Socket

        try{
            this.ds = new DatagramSocket(PORT);
            System.out.println("TRANSMISSION WORKER: Online !!!!");
        }catch (SocketException e){
            System.out.println("Error in Socket initialization!!");
            e.printStackTrace();
            System.exit(-1);
        }

        // Initialize the two slaves
        ReceiverSlave rs = new ReceiverSlave(ds,inputQueue);
        Thread receiver = new Thread(rs);
        receiver.start();

        SenderSlave ss = new SenderSlave(ds,outputQueue);
        Thread senderSlave= new Thread(ss);
        senderSlave.start();



        // Change this to properly close down worker
        boolean working = true;
        while (working) {


            // Awaits for a datagram to be put on the queue
            DatagramPacket input = inputQueue.popElem();

            /////// Start processing

            // Origin IP of the packet// Had to remove the first character
            String ip = input.getAddress().toString().replace("/","");


            // Package that will be sent
            FTRapidV2 newPackage = null;

            // If the package comes from a server
            if (this.clientTable.getAllServers().contains(ip)) {
                // Wraps the RTP package in a FTRapid one
                long timestamp =System.currentTimeMillis();
                newPackage = new FTRapidV2(timestamp,timestamp, 0, input.getData(), input.getLength(),ip);

            }
            // Else if it comes from a neighbour
            else if (this.neighbourTable.getNeighbours().contains(ip)) {
                System.out.println("TransmissionLayer: Pacote recebido de outro Nodo com IP:"+ ip);

                FTRapidV2 oldPacket = new FTRapidV2(input.getData(), input.getLength());
                long initTimeSt = oldPacket.getInitialTimeSt();
                int jumps = oldPacket.getJumps();
                String serverIP = oldPacket.getServerIP();
                long currTime = System.currentTimeMillis();

                // Updates the routing table with the time it took the packet to reach the current node
                this.routingTable.updateMetrics(serverIP,ip,jumps,currTime-initTimeSt);

                // Update neighbour jump // Here we could detect a delay in the jump
                this.neighbourTable.updateLastJumpTime(ip,currTime - oldPacket.getLastJumpTimeSt());

                newPackage = new FTRapidV2(initTimeSt,currTime,jumps+1, oldPacket.getPayload(), oldPacket.getPayloadLength(),serverIP );
            }


            // If the package was processed correctly, send it
            if (newPackage != null) {

                // Send to all neighbours that want the packet
                List<String> nodeList = neighbourTable.getNeighboursWantingStream();
                System.out.println("TransmissionLayer: Node list "+ nodeList);
                for (String ipDest : nodeList) {
                    try {
                        DatagramPacket output = new DatagramPacket(newPackage.getData(), newPackage.getLength(), InetAddress.getByName(ipDest), PORT);
                        // Coloca datagrama na queue para envio
                        outputQueue.pushElem(output);
                    } catch (UnknownHostException ignored) {}
                }

                // Send to all clients
                List<String> clientList = this.clientTable.getAllClients();
                System.out.println("TransmissionLayer: Client list "+ clientList);
                for (String ipDest : clientList) {
                    try {
                        DatagramPacket output = new DatagramPacket(newPackage.getPayload(), newPackage.getPayloadLength(), InetAddress.getByName(ipDest), CLPORT);
                        // Coloca datagrama na queue para envio
                        outputQueue.pushElem(output);
                    } catch (UnknownHostException ignored) {}
                }
            }
        }
    }

    public boolean detectDelay(String origin,float delayPercent, long timestamp){
        long supposedTime = this.neighbourTable.getLastJumpTime(origin);
        return supposedTime * (1+delayPercent) < timestamp || supposedTime * delayPercent > timestamp;
    }



}
