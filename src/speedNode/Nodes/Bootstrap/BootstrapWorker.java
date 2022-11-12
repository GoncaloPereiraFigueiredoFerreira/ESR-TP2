package speedNode.Nodes.Bootstrap;

import speedNode.Nodes.Serialize;
import speedNode.TaggedConnection.Frame;
import speedNode.TaggedConnection.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

public class BootstrapWorker implements Runnable{
    BootstrapSharedInfo sharedInfo;
    Socket socket;
    TaggedConnection connection;

    /*
        | TAG | DESCRIPTION
        |-----+------------------
        |  1  | Response to the request "Get Neighbours"
        |-----+-----------------
        |  5  | Indicates that can start flooding
    */

    public BootstrapWorker(BootstrapSharedInfo sharedInfo, Socket socket) throws IOException {
        this.sharedInfo = sharedInfo;
        this.socket = socket;
        this.connection = new TaggedConnection(socket);
    }

    @Override
    public void run() {
        try {
            Frame frame = connection.receive();
            System.out.println("Received with number:" + frame.getNumber() + " | tag: " + frame.getTag() + " | content: " + Arrays.toString(frame.getData()));

            // Mais tarde pode ser um switch quando tiver mais opcoes
            switch (frame.getTag()) {
                case 1:
                    sendNeighbours(frame);
                    break;
                case 2:
                    /*
                    //If the node is a server, then awaits to send message granting permission to flood
                    boolean isServer = Serialize.deserializeBoolean(frame.getData());
                    if(isServer){
                        sharedInfo.canStartFlood();
                        connection.send(0, 5, new byte[]{});
                    }
                    */
                    break;
            }

            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendNeighbours(Frame frame) throws IOException {
        System.out.println(socket.getInetAddress().getHostAddress()); //TODO - tirar print

        List<String> vizinhos = sharedInfo.getNeighbours(socket.getInetAddress().getHostAddress());
        connection.send(0,1, Serialize.serializeListOfStrings(vizinhos));
        System.out.println("Sent request with tag 1");
    }
}
