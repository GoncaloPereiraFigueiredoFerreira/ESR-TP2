package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Utilities.Serialize;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

public class FTRapidV2 {
    static int HEADER_SIZE = 28;

    static int PackType = 420;
    public InetAddress ServerIp;
    public long InitialTimeSt =0;
    public long LastJumpTimeSt =0;
    public int Jumps=0;
    // public int EncryptionKey;

    public byte[] header;
    public int payload_size;
    public byte[] payload;

    //TODO: Implement encryption on payload


    public FTRapidV2(long initialTimeStamp,long lastJumpTimeSt, int jumps, byte[] rtppacket, int rtpLen,String ServerIp) {
        header = new byte[HEADER_SIZE];
        this.Jumps= jumps;
        this.LastJumpTimeSt = lastJumpTimeSt;
        this.InitialTimeSt = initialTimeStamp;
        this.payload = rtppacket;
        this.payload_size = rtpLen;

        try{
            this.ServerIp = InetAddress.getByName(ServerIp);
        }catch (UnknownHostException ignored){} // Nunca vai dar este erro

        try {
            byte[] typeB = Serialize.serializeInteger(PackType);
            byte[] timeStIn   = Serialize.serializeLong(this.InitialTimeSt);
            byte[] timeStJump = Serialize.serializeLong(this.LastJumpTimeSt);
            byte[] jumpB = Serialize.serializeInteger(this.Jumps);
            byte[] ip = this.ServerIp.getAddress();
            int i =0;
            for (byte b : typeB) {header[i] = b;i++;}
            for (byte b : timeStIn) {header[i] = b;i++;}
            for (byte b : timeStJump) {header[i] = b;i++;}
            for (byte b : jumpB) {header[i] = b;i++;}
            for (byte b : ip) {header[i] = b;i++;}
        } catch (IOException e) {
            //TODO
            e.printStackTrace();
        }
    }

    public FTRapidV2(byte[] ftrapidV2, int ftrapid_length) {
        try {
            this.InitialTimeSt = Serialize.deserializeLong(Arrays.copyOfRange(ftrapidV2,4,12));
            this.LastJumpTimeSt = Serialize.deserializeLong(Arrays.copyOfRange(ftrapidV2,12,20));
            this.Jumps = Serialize.deserializeInteger(Arrays.copyOfRange(ftrapidV2,20,24));
            this.ServerIp = InetAddress.getByAddress(Arrays.copyOfRange(ftrapidV2,24,28));
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert this.InitialTimeSt !=0;
        assert this.LastJumpTimeSt !=0;
        assert this.Jumps !=0;

        this.header = Arrays.copyOfRange(ftrapidV2,0,HEADER_SIZE);
        this.payload = Arrays.copyOfRange(ftrapidV2,HEADER_SIZE,ftrapid_length);
        this.payload_size = ftrapid_length-HEADER_SIZE;
    }



    public long getLastJumpTimeSt(){
        return this.LastJumpTimeSt;
    }

    public int getJumps(){
        return this.Jumps;
    }

    public byte[] getPayload() {
        return payload;
    }
    public int getPayloadLength() {
        return payload_size;
    }


    public byte[] getData(){
        byte[]  data =  new byte[HEADER_SIZE+this.payload_size];
        int i =0;
        for (byte b : this.header) {data[i]=b; i++;}
        for (byte b : this.payload) {data[i]=b; i++;}
        return data;
    }

    public int getLength(){
        return HEADER_SIZE+this.payload_size;
    }

    public String getServerIP(){
        return this.ServerIp.toString();
    }


    public long getInitialTimeSt() {
        return InitialTimeSt;
    }
}
