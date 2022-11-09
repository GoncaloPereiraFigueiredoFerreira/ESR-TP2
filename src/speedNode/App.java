package speedNode;

import speedNode.Nodes.Bootstrap.Bootstrap;
import speedNode.Nodes.OverlayNode.OverlayNode;

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

        if(modes.size() == 0){
            System.out.println("No valid program was given!\n" + listPrograms());
            return;
        }

        modes.forEach((key, value) -> new Thread(() -> {
            switch (key) {
                case "--node" -> OverlayNode.launch(value);
                case "--bootstrap" -> Bootstrap.launch(value);
                default -> System.out.println("Invalid program \"" + key + "\"\n" + listPrograms());
            }
        }).start());
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
