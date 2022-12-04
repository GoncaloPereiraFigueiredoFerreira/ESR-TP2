package speedNode.Nodes.Bootstrap;

import speedNode.Utilities.Serialize;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;

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
        |  1  | Request to the request "Get Neighbours"
        |-----+-----------------
        |  4  | Indicates to the bootstrap that the node is ready (connected to all neighbours)
        |-----+-----------------
        |  7  | Indicates to the bootstrap that a server has connected to the node
    */

    public BootstrapWorker(BootstrapSharedInfo sharedInfo, Socket socket) throws IOException {
        this.sharedInfo = sharedInfo;
        this.socket = socket;
        this.connection = new TaggedConnection(socket);
    }

    //TODO - Retificar tudo depois de acabar OverlayNode
    @Override
    public void run() {
        try {
            Frame frame = connection.receive();
            System.out.println("Received with number:" + frame.getNumber() + " | tag: " + frame.getTag() + " | content: " + Arrays.toString(frame.getData()));

            switch (frame.getTag()) {
                case Tags.REQUEST_NEIGHBOURS_EXCHANGE:
                    sendNeighbours(frame);
                    break;

                case Tags.INFORM_READY_STATE:
                    // Frame telling the bootstrap that the node is ready
                    sharedInfo.addContactedNode(socket.getInetAddress().getHostAddress());
                    break;

                case Tags.FLOOD_PERMISSION_EXCHANGE:
                    // Frame telling bootstrap that the node is a server
                    sharedInfo.canStartFlood();
                    connection.send(new Frame(0,Tags.FLOOD_PERMISSION_EXCHANGE,new byte[]{}));
            }

            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendNeighbours(Frame frame) throws IOException {
        System.out.println(socket.getInetAddress().getHostAddress()); //TODO - tirar print

        List<String> vizinhos = sharedInfo.getNeighbours(socket.getInetAddress().getHostAddress());
        System.out.println(vizinhos);
        System.out.println(Serialize.deserializeListOfStrings(Serialize.serializeListOfStrings(vizinhos)));
        connection.send(0,Tags.REQUEST_NEIGHBOURS_EXCHANGE, Serialize.serializeListOfStrings(vizinhos));
        System.out.println("Sent request with tag " + Tags.REQUEST_NEIGHBOURS_EXCHANGE);
    }
}
