package speedNode.Nodes;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class Serialize {


    public static byte[] serialize (List<String> ips) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);
        out.writeInt(ips.size());
        for (String ip:ips){
            out.writeUTF(ip);
        }
        out.flush();
        byte[] byteArray= baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;

    }

    public static List<String> deserialize(byte[] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bais);
        int tamanho =  in.readInt();
        List<String> ips= new ArrayList<>();

        for(int i=0;i<tamanho;i++){
            ips.add(in.readUTF());
        }
        in.close();
        bais.close();
        return ips;
    }
}
