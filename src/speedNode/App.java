package speedNode;

import speedNode.Nodes.Bootstrap.Bootstrap;
import speedNode.Nodes.OverlayNode.OverlayNode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hello world!
 *
 */
public class App 
{

    private static String listPrograms(){
        return "Programs:\n" +
                "\t-> Bootstrap\n" +
                "\t-> Node\n";
    }

    public static void main( String[] args )
    {
        if(args.length == 0) {
            System.out.println("Inform the program you want to run as the first argument, followed by the required arguments.\n" +
                    listPrograms());
            return;
        }
        List<String> listArgs = new ArrayList<>(Arrays.asList(args));
        listArgs.remove(0);

        switch (args[0]){
            case "Node":
                OverlayNode.launch(listArgs);
                break;

            case "Bootstrap":
                Bootstrap.launch(listArgs);
                break;

            default:
                System.out.println("No valid program was given!\n" + listPrograms());
                break;
        }
    }
}
