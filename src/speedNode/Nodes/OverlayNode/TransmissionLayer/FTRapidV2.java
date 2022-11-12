package speedNode.Nodes.OverlayNode.TransmissionLayer;

import speedNode.Nodes.Serialize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

public class FTRapidV2 {
    static int HEADER_SIZE = 12;

    static int PackType = 420;
    public long Timestamp=0;
    public int Jumps=0;

    public byte[] header;
    public int payload_size;
    public byte[] payload;

    //TODO: Implement encryption on payload


    public FTRapidV2(long timestamp, int jumps, byte[] rtppacket, int rtpLen){
        header = new byte[HEADER_SIZE];
        this.Jumps= jumps;
        this.Timestamp = timestamp;
        this.payload = rtppacket;
        this.payload_size = rtpLen;

        try {
            byte[] typeB = Serialize.serializeInteger(PackType);
            byte[] timeB = Serialize.serializeLong(timestamp);
            byte[] jumpB = Serialize.serializeInteger(jumps);
            int i =0;
            for (byte b : typeB) {header[i] = b;i++;}
            for (byte b : timeB) {header[i] = b;i++;}
            for (byte b : jumpB) {header[i] = b;i++;}

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public FTRapidV2(byte[] ftrapidV2, int ftrapid_length){
        try {
            this.Timestamp = Serialize.deserializeLong(Arrays.copyOfRange(ftrapidV2,4,4+8));
            this.Jumps = Serialize.deserializeInteger(Arrays.copyOfRange(ftrapidV2,8+4,4+8+4));
        } catch (IOException e) {
            e.printStackTrace();
        }
        assert this.Timestamp!=0;
        assert this.Jumps !=0;

        this.header = Arrays.copyOfRange(ftrapidV2,0,HEADER_SIZE);
        this.payload = Arrays.copyOfRange(ftrapidV2,HEADER_SIZE,ftrapid_length);

        this.payload_size = ftrapid_length-HEADER_SIZE;
    }



    public long getTimestamp(){
        return this.Timestamp;
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






}
