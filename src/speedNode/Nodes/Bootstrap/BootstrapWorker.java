package speedNode.Nodes.Bootstrap;

import speedNode.Utilities.Serialize;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
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
            System.out.println("Received with number:" + frame.getNumber() + " | tag: " + frame.getTag());

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
        //All ipv4 addresses of the contacting node
        List<String> nodeAddresses = Serialize.deserializeListOfStrings(frame.getData());

        //Gets the neighbours of the contacting node, and finds the interface that the node should use for the overlay
        //The overlay address is the last element of the response list, following all the node neighbours
        List<String> responseList = null;
        for(String address : nodeAddresses){
            responseList = sharedInfo.getNeighbours(address);
            if(responseList != null) {
                responseList.add(address);
                break;
            }
        }

        if(responseList == null)
            responseList = new ArrayList<>();

        connection.send(0,Tags.REQUEST_NEIGHBOURS_EXCHANGE, Serialize.serializeListOfStrings(responseList));
        System.out.println("[Sent] tag: " + Tags.REQUEST_NEIGHBOURS_EXCHANGE + " | content: " + responseList);
    }
}
