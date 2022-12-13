package speedNode.Nodes.Bootstrap;

import speedNode.Utilities.TOTPAuth;
import speedNode.Utilities.TaggedConnection.Serialize;
import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.TaggedConnection.Tags;

import java.io.IOException;
import java.net.Socket;
import java.util.List;

public class BootstrapWorker implements Runnable{
    BootstrapSharedInfo sharedInfo;
    Socket socket;
    TaggedConnection connection;
    TOTPAuth authenticator = new TOTPAuth("O Alex é lindo");

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
                case Tags.REQUEST_NEIGHBOURS_EXCHANGE -> handleRequestNeighbours(frame);
                case Tags.INFORM_READY_STATE -> handleRequestStartPermission();
            }
            socket.close();
        } catch (IOException ignored) {}
    }

    private void handleRequestNeighbours(Frame frame) throws IOException {
        //Finds the name of the node by its interface and
        // gets the neighbours of the contacting node. For each neighbour, gets its name, the interface
        // that the node should use to contact the neighbour and which
        // interface of the neighbour should be used to establish the connection

        boolean authenticated = authenticator.validateMessage(frame.getData());
        if (authenticated) {

            List<String> responseList = sharedInfo.getNameAndNeighbours(socket.getInetAddress().getHostAddress());

            //If responseList == null, then the node does not belong to the overlay
            if (responseList == null) return;

            connection.send(0, Tags.REQUEST_NEIGHBOURS_EXCHANGE, Serialize.serializeListOfStrings(responseList));
            System.out.println("[Sent] tag: " + Tags.REQUEST_NEIGHBOURS_EXCHANGE + " | content: " + responseList);
        }
        else{
            System.out.println("Request not authenticated");
        }
    }

    private void handleRequestStartPermission() throws IOException {
        boolean permission;
        try {
            // Frame telling the bootstrap that the node is ready
            sharedInfo.addContactedNode(socket.getInetAddress().getHostAddress());
            sharedInfo.waitForStartConditions();
            permission = true;
        }catch (InterruptedException ie){ permission = false; }
        connection.send(0, Tags.START_PERMISSION, Serialize.serializeBoolean(permission));
    }
}
