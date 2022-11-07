package speedNode.Nodes.Bootstrap;

import speedNode.Nodes.Serialize;
import speedNode.TaggedConnection.Frame;
import speedNode.TaggedConnection.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public class BootstrapWorker implements Runnable{
    AtomicBoolean closeServer;
    Socket socket;
    TaggedConnection connection;
    Map<String,List<String>> nodesMap;

    /*
        | TAG | DESCRIPTION
        |-----+------------------
        |  1  |  Request neighbours
    */

    public BootstrapWorker(AtomicBoolean closeServer, Socket socket, Map<String,List<String>> nodesMap) throws IOException {
        this.closeServer = closeServer;
        this.socket = socket;
        this.connection = new TaggedConnection(socket);
        this.nodesMap = nodesMap;
    }

    @Override
    public void run() {
        try {
            Frame frame = connection.receive();
            System.out.println("Received with number:" + frame.getNumber() + " | tag: " + frame.getTag() + " | content: " + Arrays.toString(frame.getData()));

            // Mais tarde pode ser um switch quando tiver mais opcoes
            if(frame.getTag() == 1){
                System.out.println(socket.getInetAddress().getHostAddress()); //TODO - tirar print
                List<String> vizinhos = nodesMap.get(socket.getInetAddress().getHostAddress());
                if(vizinhos == null)
                    vizinhos = new ArrayList<>();
                connection.send(0,1, Serialize.serialize(vizinhos));
                System.out.println("Sent request with tag 1");
            }

            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
