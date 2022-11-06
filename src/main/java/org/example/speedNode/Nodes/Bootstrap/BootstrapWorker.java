package org.example.speedNode.Nodes.Bootstrap;

import org.example.speedNode.Nodes.serialize;
import org.example.speedNode.TaggedConnection.Frame;
import org.example.speedNode.TaggedConnection.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
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
            Frame f = connection.receive();

            // Mais tarde pode ser um switch quando tiver mais opcoes
            if(f.getTag() != 1){
                List<String> vizinhos = nodesMap.get(socket.getInetAddress().getHostAddress());
                connection.send(0,1, serialize.serialize(vizinhos));
            }

            socket.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
