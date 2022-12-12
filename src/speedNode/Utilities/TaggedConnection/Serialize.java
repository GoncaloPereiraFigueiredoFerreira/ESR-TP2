package speedNode.Utilities.TaggedConnection;

import java.io.*;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Serialize {

    public static byte[] serializeInteger(int number) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        out.writeInt(number);
        out.flush();

        byte[] byteArray= baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;
    }

    public static byte[] serializeLong(long number) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        out.writeLong(number);
        out.flush();

        byte[] byteArray= baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;
    }

    public static int deserializeInteger(byte[] buffer) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        ObjectInputStream in = new ObjectInputStream(bais);

        int anInt = in.readInt();

        in.close();
        bais.close();
        return anInt;
    }

    public static long deserializeLong(byte[] buffer) throws IOException {
        ByteArrayInputStream bais = new ByteArrayInputStream(buffer);
        ObjectInputStream in = new ObjectInputStream(bais);

        long anlong = in.readLong();

        in.close();
        bais.close();
        return anlong;
    }


    public static byte[] serializeBoolean (boolean bool) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        out.writeBoolean(bool);
        out.flush();

        byte[] byteArray= baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;
    }

    public static boolean deserializeBoolean(byte[] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bais);

        boolean isServer = in.readBoolean();

        in.close();
        bais.close();
        return isServer;
    }

    public static byte[] serializeString(String string) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        out.writeUTF(string);
        out.flush();

        byte[] byteArray = baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;
    }

    public static String deserializeString(byte[] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bais);
        String string = in.readUTF();
        in.close();
        bais.close();
        return string;
    }

    public static byte[] serializeListOfStrings (List<String> strings) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream out = new ObjectOutputStream(baos);

        if(strings != null) {
            out.writeInt(strings.size());
            for (String ip : strings) {
                out.writeUTF(ip);
            }
        } else out.writeInt(0);
        out.flush();

        byte[] byteArray = baos.toByteArray();
        out.close();
        baos.close();
        return byteArray;
    }

    public static List<String> deserializeListOfStrings (byte[] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        ObjectInputStream in = new ObjectInputStream(bais);

        int tamanho =  in.readInt();
        List<String> strings = new ArrayList<>();
        for(int i=0;i<tamanho;i++)
            strings .add(in.readUTF());

        in.close();
        bais.close();
        return strings ;
    }

    public static byte[] serializeListOfIPs (List<String> ips) throws IOException{
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ips.forEach(x -> {
            try {
                baos.writeBytes(InetAddress.getByName(x).getAddress());
            } catch (UnknownHostException e) {
                System.out.println("IP não reconhecido !! " + x);
            }
        });
        byte[] byteArray= baos.toByteArray();
        baos.close();
        return byteArray;

    }


    public static List<String> deserializeListOfIPs(byte[] bytes) throws IOException{
        ByteArrayInputStream bais = new ByteArrayInputStream(bytes);
        List<String> list = new ArrayList<>();
        int i =0;
        while (i < bytes.length){
            list.add(InetAddress.getByAddress(bais.readNBytes(4)).getHostAddress());
            i+=4;
        }
        bais.close();
        return list;
    }




}
