package speedNode.Nodes.Bootstrap;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import speedNode.Utilities.Tuple;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Bootstrap implements Runnable {
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

            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Socket s = ss.accept();
                    s.setSoTimeout(socketSoTimeout);

                    try {
                        Thread t = new Thread(new BootstrapWorker(sharedInfo, s));
                        t.start();
                        threads.add(t);
                    } catch (IOException ignored) {
                    }
                } catch (IOException ignored) {
                }
            }

            ss.close();

            //Interrupts threads that are running
            for (Thread t : threads)
                if (t.isAlive() && !t.isInterrupted())
                    t.interrupt();

            //Waits for threads to stop running and join them
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException ignored) {
                }
            }

        } catch (IOException ioe) {
            System.out.println("Could not initialize server socket!");
        }
    }


    // *************** Launch Bootstrap ***************
    public static void launch(List<String> args) {
        String filename = "bootstrap.xml";

        for (int i = 0; i < args.size(); i++) {
            switch (args.get(i)) {
                case "-filename":
                case "-file":
                case "-f":
                    if (i + 1 < args.size()) {
                        filename = args.get(i + 1);
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
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }


    // *************** Read Config .XML file ***************
    public static Tuple<Map<String, Tuple<Set<String>, Set<String>>>, Integer> readConfigFile(String filename) throws Exception {

        Map<String, Tuple<Set<String>, Set<String>>> nodesMap = new HashMap<>();
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

            Node name;

            Set<String> ips_interfaces = new HashSet<>();
            Set<String> name_vizinhos = new HashSet<>();
            var attributes = node.getAttributes();

            if (attributes == null || (name = attributes.getNamedItem("name")) == null)
                throw new Exception("Node needs to have an attribute \"name\".");

            var vizinhos = node.getChildNodes();
            for (int j = 0; j < vizinhos.getLength(); j++) {
                Node vizinho = vizinhos.item(j);
                if (vizinho != null)
                    attributes = vizinho.getAttributes();
                if (vizinho != null && vizinho.getNodeName().equals("interface"))
                    ips_interfaces.add(attributes.getNamedItem("ip").getTextContent());
                else if (vizinho != null && vizinho.getNodeName().equals("neighbour"))
                    name_vizinhos.add(attributes.getNamedItem("name").getTextContent());
            }
            Tuple<Set<String>, Set<String>> tuple = new Tuple<>(ips_interfaces, name_vizinhos);
            nodesMap.put(name.getTextContent(), tuple);
        }
        Integer number_nodes;
        try {
            String nodes = doc.getElementsByTagName("overlay").item(0).getAttributes().getNamedItem("nodes").getTextContent();
            number_nodes = Integer.parseInt(nodes);
        } catch (NullPointerException | NumberFormatException e) {
            number_nodes = nodesMap.size();
        }
        return new Tuple<>(nodesMap, number_nodes);
    }
}
