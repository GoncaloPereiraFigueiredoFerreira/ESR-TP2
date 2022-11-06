package org.example.speedNode.Nodes.Bootstrap;

import org.example.speedNode.TaggedConnection.Frame;
import org.example.speedNode.TaggedConnection.TaggedConnection;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class BootstrapWorker implements Runnable{
    AtomicBoolean closeServer;
    TaggedConnection tc;

    /*
        | TAG | DESCRIPTION
        |-----+------------------
        |  1  |  Request neighbours
    */

    public BootstrapWorker(AtomicBoolean closeServer, Socket s) throws IOException {
        this.closeServer = closeServer;
        this.tc = new TaggedConnection(s);
    }

    @Override
    public void run() {
        try {
            Frame f = tc.receive();


        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
