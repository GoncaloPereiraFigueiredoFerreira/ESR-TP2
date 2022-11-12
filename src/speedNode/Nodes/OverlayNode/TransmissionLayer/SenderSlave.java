package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Nodes.ProtectedQueue;
import speedNode.Nodes.Tables.INeighbourTable;

import javax.xml.crypto.Data;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.util.List;

public class SenderSlave  implements Runnable{
    private final DatagramSocket ds ;
    private final ProtectedQueue<DatagramPacket> outputQueue;

    public SenderSlave(DatagramSocket ds, ProtectedQueue<DatagramPacket> outputQueue){
        this.ds = ds;
        this.outputQueue = outputQueue;
    }


    @Override
    public void run() {
        // Fica a espera de uma adição na outputqueue
        boolean working=true;
        while (working) {

            // Waits for the outputqueue to have at least one datagram inside
            outputQueue.awaitPush();
            DatagramPacket dp = outputQueue.popElem();
            try {
                ds.send(dp);
            } catch (IOException ignored) {}
        }

    }
}