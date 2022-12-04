package speedNode.Nodes.Bootstrap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Bootstrap implements Runnable{
    private ServerSocket ss;
    private final int ServerPort = 12345;
    private int soTimeout = 0; //ServerSocket Timeout
    private int socketSoTimeout = 0; //Socket Timeout - O means infinite

    private BootstrapSharedInfo sharedInfo;

    public Bootstrap(String Bootstrap_config_filename) throws Exception {
        sharedInfo = new BootstrapSharedInfo(readConfigFile(Bootstrap_config_filename));
    }


    // *************** Run Bootstrap ***************
    @Override
    public void run() {
        Set<Thread> threads = new HashSet<>();
        try {
            //Start Server Socket
            ss = new ServerSocket(ServerPort);
            ss.setSoTimeout(soTimeout);

            while (!sharedInfo.isServerClosed()) {
                try {
                    Socket s = ss.accept();
                    s.setSoTimeout(socketSoTimeout);

                    //TODO - see how to wake threads so they can check the closeServer bool and end on command
                    try {
                        Thread t = new Thread(new BootstrapWorker(sharedInfo, s));
                        t.start();
                        threads.add(t);
                    }
                    catch (IOException ignored){}

                    // TODO - Update implementation when possible
                    // For the first implementation which requires all nodes to be on to start the flood.
                    //sharedInfo.addContactedNode(s.getInetAddress().getHostAddress());


                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            ss.close();
            sharedInfo.setServerClosed(true);
            for(Thread t : threads) {
                try { t.join(); }
                catch (InterruptedException ie){ ie.printStackTrace(); }
            }
        }catch (IOException ioe){
            System.out.println("Could not initialize server socket!");
        }
    }


    // *************** Launch Bootstrap ***************
    public static void launch(List<String> args) {
        String filename = "bootstrap.xml";

        for(int i = 0; i < args.size(); i++){
            switch (args.get(i)){
                case "-filename":
                    if(i + 1 < args.size()){
                        filename = args.get(i+1);
                        i++;
                    }
                    break;

                case "-help":
                    System.out.println("Use the flag '-filename' to specify the path to the .xml config file.");
            }
        }

        try {
            Bootstrap bootstrap = new Bootstrap(filename);
            bootstrap.run();
        }catch (Exception e){
            System.out.println(e.getMessage());
        }
    }


    // *************** Read Config .XML file ***************
    public static Map<String, List<String>> readConfigFile(String filename) throws Exception {

        Map<String, List<String>> nodesMap = new HashMap<>();

        File file = new File(filename);

        if (!file.exists())
            throw new Exception("File '" + filename + "' not found!");

        //an instance of factory that gives a document builder
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();

        //an instance of builder to parse the specified xml file
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(file);
        doc.getDocumentElement().normalize();

        NodeList nodeList = doc.getElementsByTagName("node");
        for (int itr = 0; itr < nodeList.getLength(); itr++) {
            Node node = nodeList.item(itr);
            Node ip;
            List<String> ips_vizinhos = new ArrayList<>();

            var attributes = node.getAttributes();

            if (attributes == null || (ip = attributes.getNamedItem("ip")) == null)
                throw new Exception("Node needs to have an attribute \"ip\".");

            var vizinhos = node.getChildNodes();
            for (int j = 0; j < vizinhos.getLength(); j++) {
                Node vizinho = vizinhos.item(j);
                if (vizinho != null && vizinho.getNodeName().equals("ip"))
                    ips_vizinhos.add(vizinho.getTextContent());
            }

            nodesMap.put(ip.getTextContent(), ips_vizinhos);
        }

        return nodesMap;
    }
}
