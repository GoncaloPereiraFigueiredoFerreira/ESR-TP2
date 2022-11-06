package org.example.speedNode.Nodes.Bootstrap.Config;

import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.List;

public class BootstrapConfig {
    private List<NodeInfo> nodes;

    public BootstrapConfig(){}

    public List<NodeInfo> getNodes() {
        return nodes;
    }

    public void setNodes(List<NodeInfo> nodes) {
        this.nodes = nodes;
    }

    public static BootstrapConfig readFile(String filename) throws FileNotFoundException {
        InputStream inputStream = new FileInputStream(new File(filename));
        Yaml yaml = new Yaml(new Constructor(BootstrapConfig.class));
        return yaml.load(inputStream);
    }

    @Override
    public String toString() {
        return "Config{" +
                "nodes=" + nodes +
                '}';
    }


}
