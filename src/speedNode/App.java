package speedNode;

import speedNode.Nodes.Bootstrap.Bootstrap;
import speedNode.Nodes.OverlayNode.ControlLayer.OverlayNode;
import speedNode.Nodes.Server.TestServer;

import java.util.*;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        if(args.length == 0) {
            System.out.println("Inform the program you want to run as the first argument, followed by the required arguments.\n" +
                    listPrograms());
            return;
        }

        Map<String,List<String>> modes = extractModesAndArgs(args);

        //If no modes are detected, exit the program
        if(modes.size() == 0){
            System.out.println("No valid program was given!\n" + listPrograms());
            return;
        }

        //TODO - meter isto direito - so deve aceitar o launch de um tipo

        boolean anyValidArguments = false;

        if(modes.containsKey("--bootstrap")) {
            System.out.println("bootstrap: " + modes.get("--bootstrap"));
            new Thread(() -> Bootstrap.launch(modes.get("--bootstrap"))).start();
            anyValidArguments = true;
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if(modes.containsKey("--node")){
            System.out.println("node: " + modes.get("--node"));
            new Thread(() -> OverlayNode.launch(modes.get("--node"))).start();
            anyValidArguments = true;
        }

        if(modes.containsKey("--server")){
            System.out.println("server: " + modes.get("--server"));
            new Thread(() -> TestServer.launch(modes.get("--server"))).start();
            anyValidArguments = true;
        }


        if(!anyValidArguments)
            System.out.println("Invalid program!\n" + listPrograms());
    }

    private static String listPrograms(){
        return "Programs:\n" +
                "\t-> bootstrap\n" +
                "\t-> node\n";
    }

    private static Map<String,List<String>> extractModesAndArgs(String[] args) {
        Map<String, List<String>> modes = new HashMap<>(); //Modes and respective list of arguments
        String mode = null;
        List<String> arguments = new ArrayList<>();

        for (String arg : args) {
            if (arg.charAt(0) == '-' && arg.charAt(1) == '-') { // if the string starts with "--" then its a mode
                if (mode != null) {
                    modes.put(mode, arguments);
                    arguments = new ArrayList<>();
                }
                mode = arg;
            } else if (mode != null) // if the string start with "-" then it is an argument associated with the mode that appeared before
                arguments.add(arg);
        }

        if (mode != null)
            modes.put(mode, arguments);

        return modes;
    }
}
