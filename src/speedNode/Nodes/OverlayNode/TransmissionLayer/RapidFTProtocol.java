package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Utilities.Serialize;

import java.beans.Encoder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Base64;


public class RapidFTProtocol {
    static int HEADER_SIZE = 56;

    static int PackType = 420;
    public InetAddress ServerIp;
    public InetAddress NeighbourIp;
    public long InitialTimeSt =0;
    public long LastJumpTimeSt =0;
    public int Jumps=0;

    public byte[] header;
    public int payload_size;
    public byte[] payload;

    /**
     * Constructor for packages sent from a content provider
     * @param initialTimeStamp Recorded timestamp of arrival
     * @param lastJumpTimeSt Latest jump timestamp
     * @param jumps Number of jumps taken by the packet
     * @param rtpPacket RTP packet contained in the payload
     * @param ServerIp IP of the content provider server
     * @param NeighbourIP IP of the last speed node passed
     */
    public RapidFTProtocol(long initialTimeStamp, long lastJumpTimeSt, int jumps, byte[] rtpPacket, String ServerIp, String NeighbourIP) {
        header = new byte[HEADER_SIZE];
        this.Jumps= jumps;
        this.LastJumpTimeSt = lastJumpTimeSt;
        this.InitialTimeSt = initialTimeStamp;
        this.payload = this.encryptMessage(rtpPacket);
        this.payload_size = this.payload.length;

        try{
            this.ServerIp = InetAddress.getByName(ServerIp);
            this.NeighbourIp = InetAddress.getByName(NeighbourIP);
        }catch (UnknownHostException ignored){} // Nunca vai dar este erro

        try {
            byte[] typeB = Serialize.serializeInteger(PackType);
            byte[] timeStIn   = Serialize.serializeLong(this.InitialTimeSt);
            byte[] timeStJump = Serialize.serializeLong(this.LastJumpTimeSt);
            byte[] jumpB = Serialize.serializeInteger(this.Jumps);
            byte[] ip = this.ServerIp.getAddress();
            byte[] neighIP = this.NeighbourIp.getAddress();
            int i =0;
            for (byte b : typeB) {header[i] = b;i++;}
            for (byte b : timeStIn) {header[i] = b;i++;}
            for (byte b : timeStJump) {header[i] = b;i++;}
            for (byte b : jumpB) {header[i] = b;i++;}
            for (byte b : ip) {header[i] = b;i++;}
            for (byte b : neighIP){header[i] = b;i++;}
        } catch (IOException e) {
            //TODO
            e.printStackTrace();
        }
    }

    /**
     * Constructor to reconstruct a rapidftp
     * @param rapidFTP Rapid ftp package
     * @param rapidoFTP_length Byte length of package
     */
    public RapidFTProtocol(byte[] rapidFTP, int rapidoFTP_length) {
        try {
            this.InitialTimeSt = Serialize.deserializeLong(Arrays.copyOfRange(rapidFTP,10,24));
            this.LastJumpTimeSt = Serialize.deserializeLong(Arrays.copyOfRange(rapidFTP,24,38));
            this.Jumps = Serialize.deserializeInteger(Arrays.copyOfRange(rapidFTP,38,48));
            this.ServerIp = InetAddress.getByAddress(Arrays.copyOfRange(rapidFTP,48,52));
            this.NeighbourIp =InetAddress.getByAddress(Arrays.copyOfRange(rapidFTP,52,56));
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert this.InitialTimeSt !=0;
        assert this.LastJumpTimeSt !=0;
        assert this.Jumps !=0;

        this.header = Arrays.copyOfRange(rapidFTP,0,HEADER_SIZE);
        this.payload = Arrays.copyOfRange(rapidFTP,HEADER_SIZE,rapidoFTP_length);
        this.payload_size = this.payload.length;
    }


    public long getLastJumpTimeSt(){
        return this.LastJumpTimeSt;
    }

    public int getJumps(){
        return this.Jumps;
    }

    public byte[] getPayload() {
        return this.decryptMessage(payload);
    }

    public int getPayloadLength() {
        return this.decryptMessage(payload).length;
    }


    public byte[] getData(){
        byte[]  data =  new byte[HEADER_SIZE+this.payload_size];

        int i =0;
        for (byte b : this.header) {data[i]=b; i++;}
        for (int j=0; j<this.payload_size; j++){data[i]=this.payload[j]; i++;}

        return data;
    }

    public int getLength(){
        return HEADER_SIZE+this.payload_size;
    }

    public String getServerIP(){
        return this.ServerIp.getHostAddress();
    }

    public String getNeighbourIP(){
        return this.NeighbourIp.getHostAddress();
    }

    public long getInitialTimeSt() {
        return InitialTimeSt;
    }

    public byte[] encryptMessage(byte[] message) {
        Base64.Encoder e = Base64.getEncoder();
        return e.encode(message);
    }

    public byte[] decryptMessage(byte[] message){
        Base64.Decoder e = Base64.getDecoder();
        return e.decode(message);
    }

}
