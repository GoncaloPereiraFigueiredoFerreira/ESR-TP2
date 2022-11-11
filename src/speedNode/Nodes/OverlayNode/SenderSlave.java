package speedNode.Nodes.OverlayNode;

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
            if (outputQueue.length() == 0) {
                // Fica bloqueado a espera de pacotes na queue
                try {
                    outputQueue.awaitSignal();
                } catch (InterruptedException ignored) {}
            }
            DatagramPacket dp = outputQueue.popElem();
            try {
                ds.send(dp);
            } catch (IOException ignored) {}
        }

    }
}
