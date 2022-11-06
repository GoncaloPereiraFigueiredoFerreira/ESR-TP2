package org.example.speedNode.Nodes.Node;
import org.example.speedNode.TaggedConnection.Frame;
import org.example.speedNode.TaggedConnection.TaggedConnection;
import org.example.speedNode.Nodes.Serialize;
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
            List<String> ips= Serialize.deserialize(frame.getData());
            System.out.println(ips); //TODO - remover print
        } catch (IOException e) {
            e.printStackTrace();
        }

    }

    public static void main(String[] args) {
        try {
            Node node = new Node(args[1]);
            node.run();
        } catch (IOException e) {
            System.out.println("Deu m****");
        }
    }
}
