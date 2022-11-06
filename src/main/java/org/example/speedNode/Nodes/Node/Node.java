package org.example.speedNode.Nodes.Node;
import org.example.speedNode.TaggedConnection.Frame;
import org.example.speedNode.TaggedConnection.TaggedConnection;

import java.net.*;
import java.io.*;
public class Node {

    public static void main(String argv[]) throws IOException{
        Socket s = new Socket("localhost",12345);
        TaggedConnection tagcon = new TaggedConnection(s);
        byte[] b={};
        tagcon.send(0,1,b);

        Frame frame = tagcon.receive()

    }


}
