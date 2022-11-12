package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Nodes.ProtectedQueue;
import speedNode.Nodes.Tables.IClientTable;
import speedNode.Nodes.Tables.INeighbourTable;
import speedNode.Nodes.Tables.IRoutingTable;

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
    public static final int MAX_UDP_P_SIZE = 15000; // To be defined

    public TransmitionWorker(INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.clientTable = clientTable;
        this.routingTable = routingTable;
        this.neighbourTable = neighbourTable;
    }

    @Override
    public void run() {

        // Initialization of the communication Socket

        try{
            this.ds = new DatagramSocket(PORT);
        }catch (SocketException ignored){}

        // Initialize the two slaves
        ReceiverSlave rs = new ReceiverSlave(ds,inputQueue);
        SenderSlave ss = new SenderSlave(ds,outputQueue);
        rs.run();
        ss.run();

        // Change this to properly close down worker
        boolean working = true;
        while (working) {

            // Waits for queue to have at least one datagram inside
            inputQueue.awaitPush();

            // Datagram to be processed
            DatagramPacket input = inputQueue.popElem();

            /////// Start processing

            // Origin IP of the packet
            String ip = input.getAddress().toString();

            // Package that will be sent
            FTRapidV2 newPackage = null;

            // If the package comes from a server
            if (this.clientTable.getAllServers().contains(ip)) {
                // Wraps the RTP package in a FTRapid one
                newPackage = new FTRapidV2(System.currentTimeMillis(), 0, input.getData(), input.getLength());
            }
            // Else if it comes from a neighbour
            else if (this.neighbourTable.getNeighbours().contains(ip)) {
                FTRapidV2 oldPacket = new FTRapidV2(input.getData(), input.getLength());

                long supposedTime = this.neighbourTable.getLastJumpTime(ip);
                long timestamp = oldPacket.getTimestamp();
                long currTime = System.currentTimeMillis();
                long diff = currTime - timestamp;

                if (supposedTime != -1 && detectDelay(ip,0.3f,diff)){
                    // Alert delay
                }else{
                    this.neighbourTable.updateLastJumpTime(ip,diff);
                }
                newPackage = new FTRapidV2(currTime, oldPacket.getJumps() +1, oldPacket.getPayload(),oldPacket.getPayloadLength());
            }

            // If the package was processed correctly, send it
            if (newPackage != null) {
                // Send to all neighbours that want the packet
                List<String> nodeList = neighbourTable.getConnectedNeighbours();
                for (String ipDest : nodeList) {
                    try {
                        DatagramPacket output = new DatagramPacket(newPackage.getData(), newPackage.getLength(), InetAddress.getByName(ipDest), PORT);
                        // Coloca datagrama na queue para envio
                        outputQueue.pushElem(output);
                    } catch (UnknownHostException ignored) {}
                }

                // Send to all clients
                List<String> clientList = this.clientTable.getAllClients();
                for (String ipDest : clientList) {
                    try {
                        DatagramPacket output = new DatagramPacket(newPackage.getPayload(), newPackage.getPayloadLength(), InetAddress.getByName(ipDest), PORT);
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
