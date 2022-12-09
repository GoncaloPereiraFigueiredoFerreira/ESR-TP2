package streamNodes;/* ------------------
   Cliente
   usage: java Cliente
   adaptado dos originais pela equipa docente de ESR (nenhumas garantias)
   colocar o cliente primeiro a correr que o servidor dispara logo!
   ---------------------- */

import speedNode.Utilities.TaggedConnection.Frame;
import speedNode.Utilities.TaggedConnection.TaggedConnection;
import speedNode.Utilities.Tags;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;

public class StreamClient {

    //GUI
    //----
    JFrame f = new JFrame("Cliente de Testes");
    JButton setupButton = new JButton("Setup");
    JButton playButton = new JButton("Play");
    JButton pauseButton = new JButton("Pause");
    JButton tearButton = new JButton("Teardown");
    JPanel mainPanel = new JPanel();
    JPanel buttonPanel = new JPanel();
    JLabel iconLabel = new JLabel();
    ImageIcon icon;


    //RTP variables:
    //----------------
    DatagramPacket rcvdp; //UDP packet received from the server (to receive)
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packet
    static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets

    Timer cTimer; //timer used to receive data from the UDP socket
    byte[] cBuf; //buffer used to store data received from the server

    static String SpeedNodeIP;
    static int TCPPort;


    //--------------------------
    //Constructor
    //--------------------------
    public StreamClient(String speedNodeIP, int tcpPort) {

        SpeedNodeIP = speedNodeIP;
        TCPPort = tcpPort;
        //Contactar o servidor primeiro antes da GUI aparecer
        connectToOverlay();

        //build GUI
        //--------------------------

        //Frame
        f.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(0);
            }
        });

        //Buttons
        buttonPanel.setLayout(new GridLayout(1,0));
        buttonPanel.add(setupButton);
        buttonPanel.add(playButton);
        buttonPanel.add(pauseButton);
        buttonPanel.add(tearButton);

        // handlers... (so dois)
        playButton.addActionListener(new playButtonListener());
        tearButton.addActionListener(new tearButtonListener());

        //Image display label
        iconLabel.setIcon(null);

        //frame layout
        mainPanel.setLayout(null);
        mainPanel.add(iconLabel);
        mainPanel.add(buttonPanel);
        iconLabel.setBounds(0,0,380,280);
        buttonPanel.setBounds(0,280,380,50);

        f.getContentPane().add(mainPanel, BorderLayout.CENTER);
        f.setSize(new Dimension(390,370));
        f.setVisible(true);



        //init para a parte do cliente
        //--------------------------
        cTimer = new Timer(20, new clientTimerListener());
        cTimer.setInitialDelay(0);
        cTimer.setCoalesce(true);
        cBuf = new byte[15000]; //allocate enough memory for the buffer used to receive data from the server

        try {
            // socket e video
            RTPsocket = new DatagramSocket(RTP_RCV_PORT); //init RTP socket (o mesmo para o cliente e servidor)
            RTPsocket.setSoTimeout(5000); // setimeout to 5s //TODO: WHY 5s
        } catch (SocketException e) {
            System.out.println("Cliente: erro no socket: " + e.getMessage());
        }


    }

    //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception
    {
        int tcpPort = 54321;
        String ip = "";
        if (argv.length >= 1) {
            ip = argv[0];
            try {
                tcpPort = argv.length >= 2 ? Integer.parseInt(argv[1]) : 54321;
            }catch (NumberFormatException e){
                System.out.println("Client: Número de port não reconhecido!");
            }
            System.out.println("Client: Endereço IP indicado como parametro: " + ip);
            System.out.println("Client: Port TCP indicado como parametro: " + tcpPort);
            StreamClient t = new StreamClient(ip,tcpPort);
        }
        else {
            System.out.println("Client: Parametro 'IP do SpeedNode' em falta!");
        }
    }



    private int connectToOverlay(){
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


    private int disconnectfromOverlay(){
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



    //------------------------------------
    //Handler for buttons
    //------------------------------------

    //Handler for Play button
    //-----------------------
    class playButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e){

            System.out.println("Play Button pressed !");
            //start the timers ...
            cTimer.start();
        }
    }

    //Handler for tear button
    //-----------------------
    class tearButtonListener implements ActionListener {
        public void actionPerformed(ActionEvent e){

            System.out.println("Teardown Button pressed !");
            //stop the timer
            cTimer.stop();
            disconnectfromOverlay();
            //exit
            System.exit(0);
        }
    }

    //------------------------------------
    //Handler for timer (para cliente)
    //------------------------------------

    class clientTimerListener implements ActionListener {
        public void actionPerformed(ActionEvent e) {

            //Construct a DatagramPacket to receive data from the UDP socket
            rcvdp = new DatagramPacket(cBuf, cBuf.length);

            try{
                //receive the DP from the socket:
                RTPsocket.receive(rcvdp);

                //create an RTPpacket object from the DP
                RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());

                //print important header fields of the RTP packet received:
                System.out.println("Got RTP packet with SeqNum # "+rtp_packet.getsequencenumber()+" TimeStamp "+rtp_packet.gettimestamp()+" ms, of type "+rtp_packet.getpayloadtype());

                //print header bitstream:
                rtp_packet.printheader();

                //get the payload bitstream from the RTPpacket object
                int payload_length = rtp_packet.getpayload_length();
                byte [] payload = new byte[payload_length];
                rtp_packet.getpayload(payload);

                //get an Image object from the payload bitstream
                Toolkit toolkit = Toolkit.getDefaultToolkit();
                Image image = toolkit.createImage(payload, 0, payload_length);

                //display the image as an ImageIcon object
                icon = new ImageIcon(image);
                iconLabel.setIcon(icon);
            }
            catch (InterruptedIOException iioe){
                System.out.println("Nothing to read");
            }
            catch (IOException ioe) {
                System.out.println("Exception caught: "+ioe);
            }
        }
    }

}//end of Class Cliente

