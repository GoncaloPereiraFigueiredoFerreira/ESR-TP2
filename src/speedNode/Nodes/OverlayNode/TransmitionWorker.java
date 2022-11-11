package speedNode.Nodes.OverlayNode;

import speedNode.Nodes.ProtectedQueue;
import speedNode.Nodes.Tables.IClientTable;
import speedNode.Nodes.Tables.INeighbourTable;
import speedNode.Nodes.Tables.IRoutingTable;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

public class TransmitionWorker implements Runnable{
    private final INeighbourTable neighbourTable;
    private final IRoutingTable routingTable;
    private final IClientTable clientTable;
    private final ProtectedQueue<DatagramPacket> inputQueue = new ProtectedQueue<>();
    private final ProtectedQueue<DatagramPacket> outputQueue = new ProtectedQueue<>();
    private DatagramSocket ds;
    private static final int PORT=50000;
    public static final int MAX_UDP_P_SIZE = 65515; // To be defined

    public TransmitionWorker(INeighbourTable neighbourTable, IRoutingTable routingTable, IClientTable clientTable){
        this.clientTable = clientTable;
        this.routingTable = routingTable;
        this.neighbourTable = neighbourTable;
    }

    @Override
    public void run() {
        // Vai começar por criar um receiver slave.
        // Este receiver slave vai bloquear a espera de pedidos, e coloca-os na queue de entrada.
        // Entretanto, esta thread, vai esperar q apareçam pedidos na queue de entrada e quando aparecer um pedido
        // Desempacota o e volta a empacotar
        // Depois procura a lista de nodos a quem tem de enviar e envia

        ReceiverSlave rs = new ReceiverSlave(ds,inputQueue);
        SenderSlave ss = new SenderSlave(ds,outputQueue);
        rs.run();
        ss.run();
        boolean working = true;
        while (working) {

            if (inputQueue.length() == 0){
                // Fica bloqueado a espera de pacotes na queue
                try {
                    inputQueue.awaitSignal();
                } catch (InterruptedException ignored) {}
            }

            DatagramPacket input = inputQueue.popElem();

            //processamento here
            List<String> iplist = neighbourTable.getConnectedNeighbours();
            List<String> clientIpList = clientTable.getAllClients();
            iplist.addAll(clientIpList);
            for (String ipDest : iplist) {
                try {
                    DatagramPacket output = new DatagramPacket(input.getData(),input.getLength(), InetAddress.getByName(ipDest),PORT);
                    // Coloca datagrama na queue para envio
                    outputQueue.pushElem(output);
                } catch (UnknownHostException ignored) {}
            }
        }

    }
}
