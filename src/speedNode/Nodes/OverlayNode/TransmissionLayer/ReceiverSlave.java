package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Utilities.ProtectedQueue;


import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class ReceiverSlave implements Runnable{
    private final DatagramSocket ds ;
    private final ProtectedQueue<DatagramPacket> inputQueue;


    public ReceiverSlave(DatagramSocket ds, ProtectedQueue<DatagramPacket> ReceiveQueue){
        this.ds = ds;
        this.inputQueue = ReceiveQueue;
    }

    @Override
    public void run() {

        DatagramPacket dp = new DatagramPacket(new byte[TransmissionWorker.MAX_UDP_P_SIZE], TransmissionWorker.MAX_UDP_P_SIZE);
        try {
            while(!Thread.currentThread().isInterrupted()) {
                ds.receive(dp);
                inputQueue.pushElem(dp);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
