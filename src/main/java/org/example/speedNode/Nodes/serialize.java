package org.example.speedNode.Nodes;

import java.io.IOException;
import java.io.*;

public class serialize {


    public static byte[] serialize (String[] ips) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeInt(ips.length);
        for (String ip:ips){
            out.writeUTF(ip);
        }
        out.flush();
        byte[] byteArray= baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;

    }

    public static String[] deserialize(byte[] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bais);
        int tamanho =  in.readInt();
        String[] ips= new String[tamanho];
        for(int i=0;i<tamanho;i++){
            ips[i]=in.readUTF();
        }
        in.close();
        bais.close();
        return ips;
    }
}
