package org.example.speedNode.TaggedConnection;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.concurrent.locks.ReentrantLock;

public class TaggedConnection implements AutoCloseable {
    private final Socket socket;
    private final DataOutputStream dos;
    private final DataInputStream dis;

    public TaggedConnection(Socket socket) throws IOException {
        this.socket = socket;
        this.dos    = new DataOutputStream(socket.getOutputStream());
        this.dis    = new DataInputStream(socket.getInputStream());
    }

    /**
     * Envia um frame ao socket correspondente
     * @param frame Frame que se deseja enviar
     */
    public void send(Frame frame) throws IOException {
        send(frame.getNumber(), frame.getTag(),frame.getData());
    }

    /**
     * Envia um frame ao socket correspondente
     * @param number Numero da operacao
     * @param tag Tag da operacao
     * @param data Conteudo do frame
     */
    public void send(int number, int tag, byte[] data) throws IOException {
        dos.writeInt(number);
        dos.writeInt(tag);
        dos.writeInt(data.length);
        dos.write(data);
        dos.flush();
    }

    /**
     * Recebe frame do socket referente a esta conexão
     * @return Frame recebido
     */
    public Frame receive() throws IOException {
        int number, tag, dataSize;
        byte[] data;

        try{
            number   = dis.readInt();
            tag      = dis.readInt();
            dataSize = dis.readInt();
            data     = new byte[dataSize];
            dis.readFully(data);
        } catch (SocketTimeoutException ste){
            return null;
        }

        return new Frame(number, tag, data);
    }

    /**
     * Fecha a conexão com o outro socket
     */
    public void close() throws IOException {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
    }
}

