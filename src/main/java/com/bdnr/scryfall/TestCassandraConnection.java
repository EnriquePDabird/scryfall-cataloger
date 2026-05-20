package com.bdnr.scryfall;

import java.net.InetSocketAddress;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.metadata.Node;

public class TestCassandraConnection {
    public static void main(String[] args) {
        try (CqlSession session = CqlSession.builder()
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
            .withLocalDatacenter("datacenter1")
            .build()) {
            
            System.out.println("Connected!");
            for (Node node : session.getMetadata().getNodes().values()) {
                System.out.println("Node: " + node.getEndPoint() + ", broadcast: " + node.getBroadcastRpcAddress().orElse(null));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
