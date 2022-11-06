package org.example.speedNode.TaggedConnection;

public class Frame {
    private final int number;
    private final int tag;
    private final byte[] data;

    public Frame(int number, int tag, byte[] data) {
        this.number = number; this.tag = tag; this.data = data;
    }

    /**
     * @return Numero de operacao correspondente ao frame
     */
    public int getNumber() {
        return number;
    }

    /**
     * @return TAG da operacao correspondente ao frame
     */
    public int getTag() {
        return tag;
    }

    /**
     * @return Conteudo da data do frame
     */
    public byte[] getData() {
        return data.clone();
    }
}