package speedNode;

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.TaggedConnection.Tags;

import java.io.IOException;
import java.net.Socket;

public interface IConnectToSpeedNode {

    static int connectServerToOverlay(String SpeedNodeIP, int tcpPort) {
        try{
            //PORT tem de ser o do tcp
            Socket s = new Socket(SpeedNodeIP, tcpPort);
            TaggedConnection tc = new TaggedConnection(s);
            s.setSoTimeout(10000);

            tc.send(0, Tags.CONNECT_AS_SERVER_EXCHANGE,new byte[]{});

            System.out.println("Servidor: A espera de resposta do SpeedNode... ");
            Frame frame=  tc.receive();
            if(frame.getTag()==Tags.CONNECT_AS_SERVER_EXCHANGE){
                System.out.println("Servidor: SpeedNode contactado! ");
                // a ligação dps pode ser fechada
                return 1;
            }
            else return -1;
        } catch (IOException e) {
            e.printStackTrace();
            return -1;
        }
    }


    static int connectClientToOverlay(String SpeedNodeIP, int TCPPort){
        try{
            Socket s = new Socket(SpeedNodeIP, TCPPort);
            TaggedConnection tc = new TaggedConnection(s);
            s.setSoTimeout(10000);

            tc.send(0, Tags.CONNECT_AS_CLIENT_EXCHANGE,new byte[]{});
            System.out.println("Client: A espera de resposta do SpeedNode");
            Frame frame=  tc.receive();
            if(frame.getTag()==Tags.CONNECT_AS_CLIENT_EXCHANGE){
                System.out.println("Client: SpeedNode contactado");
                return 1;
            }
            else if(frame.getTag()==Tags.CANCEL_STREAM){
                System.out.println("Client: SpeedNode sem rotas disponíveis!");
                return -1;
            }
            else return -1;

        }catch (IOException ioe){

            ioe.printStackTrace();
            return -1;
        }
    }

    static int disconnectClientfromOverlay(String SpeedNodeIP, int TCPPort){
        try{
            Socket s = new Socket(SpeedNodeIP, TCPPort);
            TaggedConnection tc = new TaggedConnection(s);
            s.setSoTimeout(10000);

            tc.send(0, Tags.CLIENT_CLOSE_CONNECTION,new byte[]{});

            return 1;

        }catch (IOException ioe){
            ioe.printStackTrace();
            return -1;
        }
    }
}
