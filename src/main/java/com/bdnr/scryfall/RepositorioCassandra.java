package com.bdnr.scryfall;

import java.net.InetSocketAddress;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.BatchStatement;
import com.datastax.oss.driver.api.core.cql.BoundStatement;
import com.datastax.oss.driver.api.core.cql.DefaultBatchType;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.ResultSet;
import com.datastax.oss.driver.api.core.cql.Row;

public class RepositorioCassandra{
    private final CqlSession session;
    private final PreparedStatement insertPrincipal;
    private final PreparedStatement insertPorEdicion;


    public RepositorioCassandra() {
        this.session = CqlSession.builder()
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
            .withLocalDatacenter("datacenter1")
            .withKeyspace("mtg_catalog")
            .build();

        this.insertPrincipal = session.prepare(
            "INSERT INTO cartas (id, name, set_name, mana_cost, type_line, oracle_text, colors, usd_price, eur_price) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        );

        this.insertPorEdicion = session.prepare(
            "INSERT INTO cartas_por_edicion (set_name, name, id, usd_price, eur_price) " +
            "VALUES (?, ?, ?, ?, ?)"
        );

        System.out.println("Conectado a Cassandra. Repositorio listo.");
    }

    public void guardarCarta(CartaMtg carta, boolean silencioso) {
        
        BoundStatement statement1 = insertPrincipal.bind(
            carta.getId(), carta.getName(), carta.getSet_name(),
            carta.getMana_cost(), carta.getType_line(), carta.getOracle_text(),
            carta.getColors(), carta.getUsd_price(), carta.getEur_price()
        );

        BoundStatement statement2 = insertPorEdicion.bind(
            carta.getSet_name(), carta.getName(), carta.getId(),
            carta.getUsd_price(), carta.getEur_price()
        );

        BatchStatement batch = BatchStatement.builder(DefaultBatchType.LOGGED)
            .addStatement(statement1)
            .addStatement(statement2)
            .build();

        session.execute(batch);
        
        // Solo imprimimos si NO es silencioso
        if (!silencioso) {
            System.out.println("Lote ejecutado: " + carta.getName() + " guardada en ambas tablas.");
        }
    }

    public void imprimirTodasLasCartas() {
        System.out.println("Leyendo catálogo desde Cassandra...");
        
        // Ejecutamos una consulta directa de lectura
        ResultSet resultados = session.execute("SELECT * FROM cartas");
        
        // Iteramos fila por fila
        for (Row fila : resultados) {
            String nombre = fila.getString("name");
            String edicion = fila.getString("set_name");
            String precioEuros = fila.getString("eur_price");
            
            System.out.println("- " + nombre + " (" + edicion + ") | Precio: EUR " + precioEuros);
        }
    }
    public void imprimirCartasPorEdicion(String edicion) {
        System.out.println("\nBuscando grupo: Edición " + edicion);
        
        // Esta consulta es ultra rápida porque set_name es la Partition Key
        ResultSet resultados = session.execute("SELECT * FROM cartas_por_edicion WHERE set_name = '" + edicion + "'");
        
        for (Row fila : resultados) {
            System.out.println("- " + fila.getString("name") + " | Precio: " + fila.getString("eur_price") + " EUR");
        }
    }

    public void cerrar(){
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Conexion con Cassandra Cerrada");
        }
    }


    


}   