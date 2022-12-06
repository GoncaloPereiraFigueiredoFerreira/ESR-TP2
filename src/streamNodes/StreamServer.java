package streamNodes;/* ------------------
   Servidor
   usage: java Servidor [Video file]
   adaptado dos originais pela equipa docente de ESR (nenhumas garantias)
   colocar primeiro o cliente a correr, porque este dispara logo
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
import java.io.File;
import java.io.IOException;
import java.net.*;


public class StreamServer extends JFrame implements ActionListener {

    //GUI:
    //----------------
    JLabel label;

    //RTP variables:
    //----------------
    DatagramPacket senddp; //UDP packet containing the video frames (to send)A
    DatagramSocket RTPsocket; //socket to be used to send and receive UDP packet
    int RTP_dest_port; //destination port for RTP packets //TODO: Send to the port of the speed node
    InetAddress ClientIPAddr; //Client IP address  //TODO: define speed node IP

    static String VideoFileName; //video file to request to the server

    //Video constants:
    //------------------
    int imagenb = 0; //image nb of the image currently transmitted
    VideoStream video; //VideoStream object used to access video frames
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video
    static int FRAME_PERIOD = 42; //Frame period of the video to stream, in ms //TODO: deviam ser 24 frames por segundo ou seja de 42 em 42 ms
    static int VIDEO_LENGTH = 500; //length of the video in frames  //TODO: isto devia ser calculado // este numero está predefinido pelo video q nos deram

    Timer sTimer; //timer used to send the images at the video frame rate
    byte[] sBuf; //buffer used to store the images to send to the client

    //--------------------------
    //Constructor
    //--------------------------
    public StreamServer(String speedNodeIP, int udpPort, int tcpPort) {
        //init Frame
        super("Servidor");

        // init para a parte do servidor
        sTimer = new Timer(FRAME_PERIOD, this); //init Timer para servidor
        sTimer.setInitialDelay(0);
        sTimer.setCoalesce(true);
        sBuf = new byte[15000]; //allocate memory for the sending buffer

        try {
            RTPsocket = new DatagramSocket(); //init RTP socket
            ClientIPAddr = InetAddress.getByName(speedNodeIP); //CHANGED
            RTP_dest_port = udpPort;  //ADDED
            System.out.println("Servidor: socket " + ClientIPAddr);
            video = new VideoStream(VideoFileName); //init the VideoStream object:
            System.out.println("Servidor: vai enviar video da file " + VideoFileName);

        } catch (SocketException e) {
            System.out.println("Servidor: erro no socket: " + e.getMessage());
        } catch (Exception e) {
            System.out.println("Servidor: erro no video: " + e.getMessage());
        }

        //Handler to close the main window
        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                //stop the timer and exit
                sTimer.stop();
                System.exit(0);
            }});

        //GUI:
        label = new JLabel("Send frame #        ", JLabel.CENTER);
        getContentPane().add(label, BorderLayout.CENTER);

        int ret = contactSpeedNode(tcpPort);
        if (ret==1) sTimer.start();
        else {
            System.out.println("Servidor: Erro no contacto com o SpeedNode");
            System.exit(-1);
        }
        //Inicia a stream

    }

    //------------------------------------
    //main
    //------------------------------------
    public static void main(String argv[]) throws Exception
    {
        String ip;
        int udpPort=50000;
        int tcpPort=54321;
        //get video filename to request:
        if (argv.length >= 1){
            ip = argv[0];
            VideoFileName = argv.length >= 2 ?argv[1]:"movie.Mjpeg";
            try {
                udpPort = argv.length >= 3 ? Integer.parseInt(argv[2]) : 50000;
                tcpPort = argv.length >= 4 ? Integer.parseInt(argv[3]) : 54321;
            }catch (NumberFormatException e){
                System.out.println("Servidor: Número de port não reconhecido!");
            }
            System.out.println("Servidor: IP indicado como parametro: " + ip);
            System.out.println("Servidor: VideoFileName indicado como parametro: " + VideoFileName);
            System.out.println("Servidor: Port UDP indicado como parametro: " + udpPort);
            System.out.println("Servidor: Port TCP indicado como parametro: " + tcpPort);
        }
        else  {
           System.out.println("Parâmetros insuficientes!! Execute: streamServer <ip speed node> <streamed file> <port_number>");
           return;
        }

        File f = new File(VideoFileName);
        if (f.exists()) {
            //Create a Main object
            StreamServer s = new StreamServer(ip,udpPort,tcpPort);
            //show GUI: (opcional!)
            //s.pack();
            //s.setVisible(true);
        } else
            System.out.println("Ficheiro de video não existe: " + VideoFileName);
        while (true){}
    }

    private int contactSpeedNode(int tcpPort){
        try{
            //PORT tem de ser o do tcp
            Socket s = new Socket(ClientIPAddr, tcpPort);
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


        //------------------------
    //Handler for timer
    //------------------------
    public void actionPerformed(ActionEvent e) {
        //if the current image nb is less than the length of the video
        if (imagenb < VIDEO_LENGTH)
        {
            //update current imagenb
            imagenb++;

            try {
                //get next frame to send from the video, as well as its size
                int image_length = video.getnextframe(sBuf);

                //Builds an RTPpacket object containing the frame
                RTPpacket rtp_packet = new RTPpacket(MJPEG_TYPE, imagenb, imagenb*FRAME_PERIOD, sBuf, image_length);

                //get to total length of the full rtp packet to send
                int packet_length = rtp_packet.getlength();

                //retrieve the packet bitstream and store it in an array of bytes
                byte[] packet_bits = new byte[packet_length];
                rtp_packet.getpacket(packet_bits);

                //send the packet as a DatagramPacket over the UDP socket
                senddp = new DatagramPacket(packet_bits, packet_length, ClientIPAddr, RTP_dest_port);
                RTPsocket.send(senddp);

                System.out.println("Send frame #"+imagenb);
                //print the header bitstream
                rtp_packet.printheader();

                //update GUI
                //label.setText("Send frame #" + imagenb);
            }
            catch(Exception ex)
            {
                System.out.println("Exception caught: "+ex);
                System.exit(0);
            }
        }
        else
        {
            //if we have reached the end of the video file, stop the timer
            //sTimer.stop();
            try {
                video = new VideoStream(VideoFileName);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
            sTimer.restart();
            imagenb=0;
        }
    }

}//end of Class Servidor
