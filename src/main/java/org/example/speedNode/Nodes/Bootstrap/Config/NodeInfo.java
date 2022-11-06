package org.example.speedNode.Nodes.Bootstrap.Config;

import java.util.List;

public class NodeInfo {
    private String ip;
    private List<String> vizinhos;

    public NodeInfo(){}

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public List<String> getVizinhos() {
        return vizinhos;
    }

    public void setVizinhos(List<String> vizinhos) {
        this.vizinhos = vizinhos;
    }

    @Override
    public String toString() {
        return "NodeInfo{" +
                "ip='" + ip + '\'' +
                ", vizinhos=" + vizinhos +
                '}';
    }
}
