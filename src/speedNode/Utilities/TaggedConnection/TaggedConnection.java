package speedNode.Utilities.TaggedConnection;

import speedNode.Utilities.LoggingToFile;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;

public class TaggedConnection implements AutoCloseable {
    private final Socket socket;
    private final DataOutputStream dos;
    private final DataInputStream dis;
    private final ReentrantLock readLock = new ReentrantLock();
    private final ReentrantLock writeLock = new ReentrantLock();

    private final static Logger logger = Logger.getLogger(UUID.randomUUID().toString());

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
        try {
            writeLock.lock();
            send(frame.getNumber(), frame.getTag(),frame.getData());
        }finally {
            writeLock.unlock();
        }
    }

    /**
     * Envia um frame ao socket correspondente
     * @param number Numero da operacao
     * @param tag Tag da operacao
     * @param data Conteudo do frame
     */
    public void send(int number, int tag, byte[] data) throws IOException {
        try {
            writeLock.lock();
            dos.writeInt(number);
            dos.writeInt(tag);
            dos.writeInt(data.length);
            dos.write(data);
            dos.flush();
            logger.info("TAGGEDCONNECTION - [Sent] number: " + number + " | tag: " + tag);
        }finally {
            writeLock.unlock();
        }
    }

    /**
     * Recebe frame do socket referente a esta conexão
     * @return Frame recebido
     * @throws SocketTimeoutException
     * @throws IOException
     */
    public Frame receive() throws SocketTimeoutException,IOException {
        int number, tag, dataSize;
        byte[] data;

        try {
            readLock.lock();
            number   = dis.readInt();
            tag      = dis.readInt();
            dataSize = dis.readInt();
            data     = new byte[dataSize];
            dis.readFully(data);
            logger.info("TAGGEDCONNECTION - [Receive] number: " + number + " | tag: " + tag);
            return new Frame(number, tag, data);
        }finally {
            readLock.unlock();
        }
    }

    /**
     * Fecha a conexão com o outro socket
     */
    public void close() throws IOException {
        socket.shutdownInput();
        socket.shutdownOutput();
        socket.close();
    }

    public String getHost() {
        if(socket != null)
            return socket.getInetAddress().getHostAddress();
        else
            return null;
    }

    public Socket getSocket() { return socket; }
}

