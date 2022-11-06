package org.example.speedNode.Nodes.Node;
import org.example.speedNode.TaggedConnection.Frame;
import org.example.speedNode.TaggedConnection.TaggedConnection;
import org.example.speedNode.Nodes.serialize;
import java.net.*;
import java.io.*;
import java.util.List;

public class Node implements Runnable{
    private Socket s;
    private final int bootstrapPort = 12345;
    private final String bootstrap ;
    TaggedConnection tc;

    public Node(String server) throws IOException {
        this.bootstrap= server;
        this.s = new Socket(this.bootstrap,this.bootstrapPort);
        tc = new TaggedConnection(this.s);
    }

    public void run(){

        byte[] b={};
        try {
            tc.send(0,1,b);
            Frame frame = tc.receive();
            List<String> ips= serialize.deserialize(frame.getData());

        } catch (IOException e) {
            e.printStackTrace();
        }

    }


}
