package org.example.speedNode.Nodes.Bootstrap.Config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class BootstrapConfig {
    private List<NodeInfo> nodes;

    public BootstrapConfig(){}

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    public static BootstrapConfig readConfigFromFile(String filename) throws FileNotFoundException {
        InputStream inputStream = new FileInputStream(filename);
        Yaml yaml = new Yaml(new Constructor(BootstrapConfig.class));
        return yaml.load(inputStream);
    }

    public static Map<String, List<String>> getNodesMapFromFile (String filename) throws FileNotFoundException {
        BootstrapConfig bootstrapConfig = readConfigFromFile(filename);
        var nodes = bootstrapConfig.getNodes();
        if(nodes == null)
            return null;

        Map<String,List<String>> nodesMap = new HashMap<>();
        nodes.stream().filter(Objects::nonNull).forEach(nodeInfo -> {
            nodesMap.put(nodeInfo.getIp(), nodeInfo.getVizinhos());
        });

        return nodesMap;
    }

    @Override
    public String toString() {
        return "Config{" +
                "nodes=" + nodes +
                '}';
    }


}
