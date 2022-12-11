package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Utilities.ProtectedQueue;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

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

        while (!Thread.currentThread().isInterrupted()) {

            // Waits for the outputqueue to have at least one datagram inside
            DatagramPacket dp = outputQueue.popElem();
            try {
                ds.send(dp);
            } catch (IOException ignored) {}
        }

    }
}
