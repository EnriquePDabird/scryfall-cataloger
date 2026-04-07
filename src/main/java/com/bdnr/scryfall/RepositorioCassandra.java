package com.bdnr.scryfall;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import java.net.InetSocketAddress;

public class RepositorioCassandra{
    private final CqlSession session;
    private final PreparedStatement insertStatement;


    public RepositorioCassandra() {
        this.session = CqlSession.builder()
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
            .withLocalDatacenter("datacenter1")
            .withKeyspace("mtg_catalog")
            .build();

        this.insertStatement = session.prepare(
            "INSERT INTO cartas (id, name, set_name, mana_costs, type_line, oracle_text, colors, usd_price) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
        );

        System.out.println("Conectado a Cassandra. Repositorio listo.");
    }

    public void guardarCarta(CartaMtg carta){
        session.execute(insertStatement.bind(
            carta.getId(),
            carta.getName(),
            carta.getSet_name(),
            carta.getMana_cost(),
            carta.getType_line(),
            carta.getOracle_text(),
            carta.getColors(),
            carta.getUsd_price()
        ));
        System.out.println("Carta guardad: " + carta.getName()+ " (" + carta.getSet_name() + ")");
    }

    public void cerrar(){
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Conexion con Cassandra Cerrada");
        }
    }


    


}   