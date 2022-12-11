package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Utilities.ProtectedQueue;
import speedNode.Nodes.OverlayNode.Tables.IClientTable;
import speedNode.Nodes.OverlayNode.Tables.INeighbourTable;
import speedNode.Nodes.OverlayNode.Tables.IRoutingTable;

import java.net.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TransmissionWorker implements Runnable{
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;
    private final ProtectedQueue<DatagramPacket> inputQueue = new ProtectedQueue<>();
    private final ProtectedQueue<DatagramPacket> outputQueue = new ProtectedQueue<>();
    private DatagramSocket ds;
    private static final int PORT=50000;
    private static final int CLPORT = 25000;
    public static final int MAX_UDP_P_SIZE = 30000;
    private final String bindAddr ;
    private final Set<Thread> threads = new HashSet<>();

    public TransmissionWorker(String bindAddr, INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.clientTable = clientTable;
        this.routingTable = routingTable;
        this.neighbourTable = neighbourTable;
        this.bindAddr = bindAddr;
    }

    //TODO: Store all threads in a set, in order to shutdown gracefully
    //TODO: Add logger
    @Override
    public void run() {

        // Initialization of the communication Socket

        try{
            this.ds = new DatagramSocket(PORT);
        }catch (SocketException e){
            System.out.println("Error in Socket initialization!!");
            e.printStackTrace();
            System.exit(-1);
        }

        // Initialize the two slaves
        ReceiverSlave rs = new ReceiverSlave(ds,inputQueue);
        Thread receiver = new Thread(rs);
        threads.add(receiver);
        receiver.start();

        SenderSlave ss = new SenderSlave(ds,outputQueue);
        Thread senderSlave= new Thread(ss);
        threads.add(senderSlave);
        senderSlave.start();



        // Change this to properly close down worker
        int i=0;

        while (!Thread.currentThread().isInterrupted()) {

            // Awaits for a datagram to be put on the queue
            DatagramPacket input = inputQueue.popElem();

            /////// Start processing

            // Origin IP of the packet
            String ip = input.getAddress().getHostAddress();

            // Package that will be sent
            RapidFTProtocol newPackage = null;

            // If the package comes from a server
            if (this.clientTable.getAllServers().contains(ip)) {
                // Wraps the RTP package in a FTRapid one
                long timestamp = System.nanoTime();
                newPackage = new RapidFTProtocol(timestamp,timestamp, 0, input.getData(),ip,bindAddr);

            }
            // Else if it comes from a neighbour
            else if (this.neighbourTable.anyNeighbourUsesInterfaceIP(ip)) {

                RapidFTProtocol oldPacket = new RapidFTProtocol(input.getData(), input.getLength());
                long initTimeSt = oldPacket.getInitialTimeSt();
                int jumps = oldPacket.getJumps();
                String serverIP = oldPacket.getServerIP();
                long currTime = System.nanoTime();
                String neighbourIP = oldPacket.getNeighbourIP();

                if (this.clientTable.getAllClients().size() >0){
                    //verificar se existe delay, e alertar na routing table se sim
                    this.routingTable.verifyDelay(serverIP,neighbourIP,jumps+1,currTime-initTimeSt);
                }

                this.routingTable.updateMetrics(serverIP,neighbourIP,jumps+1,currTime-initTimeSt);
                // Update neighbour jump // Here we could detect a delay in the jump
                this.neighbourTable.updateLastJumpTime(neighbourIP,currTime - oldPacket.getLastJumpTimeSt());

                newPackage = new RapidFTProtocol(initTimeSt,currTime,jumps+1, oldPacket.getPayload(),serverIP,bindAddr );

                if (i%100==0) System.out.println("TRANSMISSION: Recebi um pacote de " + ip + " com delay de " + (currTime-initTimeSt)/1000);
                i++;
            }


            // If the package was processed correctly, send it
            if (newPackage != null) {

                // Send to all neighbours that want the packet
                List<String> nodeList = neighbourTable.getNeighboursWantingStream();

                for (String ipDest : nodeList) {
                    try {
                        String interfaceIP = this.neighbourTable.getInterfaceIp(ipDest);
                        DatagramPacket output = new DatagramPacket(newPackage.getData(), newPackage.getLength(), InetAddress.getByName(interfaceIP), PORT);
                        // Coloca datagrama na queue para envio
                        outputQueue.pushElem(output);
                    } catch (UnknownHostException ignored) {}
                }

                // Send to all clients
                List<String> clientList = this.clientTable.getAllClients();
                for (String ipDest : clientList) {
                    try {
                        DatagramPacket output = new DatagramPacket(newPackage.getPayload(), newPackage.getPayloadLength(), InetAddress.getByName(ipDest), CLPORT);
                        // Coloca datagrama na queue para envio
                        outputQueue.pushElem(output);
                    } catch (UnknownHostException ignored) {}
                }
            }
        }

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




