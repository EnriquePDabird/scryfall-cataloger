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
    private final PreparedStatement insertInventario;


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

        this.insertInventario = session.prepare(
            "INSERT INTO inventario_por_caja (nombre_caja, carta_id, nombre_carta, edicion, precio_eur, cantidad) " +
            "VALUES (?, ?, ?, ?, ?, ?)"
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

    public void ingresarStock(CartaMtg carta, String nombreCaja, int cantidad) {
        session.execute(insertInventario.bind(
            nombreCaja,
            carta.getId(),
            carta.getName(),
            carta.getSet_name(),
            carta.getEur_price(),
            cantidad
        ));
        System.out.println("📥 Ingresadas " + cantidad + "x " + carta.getName() + " en la caja: [" + nombreCaja + "]");
    }

public String obtenerEstadisticasCaja(String nombreCaja) {
        StringBuilder reporte = new StringBuilder();
        reporte.append("📦 --- ABRIENDO CAJA: ").append(nombreCaja.toUpperCase()).append(" ---\n\n");
        
        ResultSet resultados = session.execute("SELECT * FROM inventario_por_caja WHERE nombre_caja = '" + nombreCaja + "'");
        
        int totalCartasUnicas = 0;
        int volumenTotalCartas = 0;
        double valorTotalEuros = 0.0;

        for (Row fila : resultados) {
            String nombre = fila.getString("nombre_carta");
            int cantidad = fila.getInt("cantidad");
            String precioStr = fila.getString("precio_eur");
            
            double precioCarton = 0.0;
            if (precioStr != null && !precioStr.equals("null")) {
                try { precioCarton = Double.parseDouble(precioStr); } 
                catch (NumberFormatException e) { /* ignorar precios inválidos */ }
            }

            double valorFila = precioCarton * cantidad;
            volumenTotalCartas += cantidad;
            totalCartasUnicas++;
            valorTotalEuros += valorFila;

            reporte.append(String.format("- %dx %s | Precio Ud: €%s | Subtotal: €%.2f\n", cantidad, nombre, precioStr, valorFila));
        }

        reporte.append("\n-----------------------------------\n");
        reporte.append("📊 ESTADÍSTICAS DE LA CAJA:\n");
        reporte.append("Variedad: ").append(totalCartasUnicas).append(" cartas distintas.\n");
        reporte.append("Volumen: ").append(volumenTotalCartas).append(" cartones en total.\n");
        reporte.append(String.format("Valor estimado: €%.2f\n", valorTotalEuros));
        reporte.append("-----------------------------------\n");

        // Devolvemos todo el texto empaquetado
        return reporte.toString();
    }

    // Método para obtener todas las cajas que existen en la BD
    public java.util.List<String> obtenerNombresCajas() {
        java.util.List<String> cajas = new java.util.ArrayList<>();
        
        // SELECT DISTINCT lee rápidamente solo las Partition Keys sin escanear las cartas
        ResultSet resultados = session.execute("SELECT DISTINCT nombre_caja FROM inventario_por_caja");
        
        for (Row fila : resultados) {
            cajas.add(fila.getString("nombre_caja"));
        }
        return cajas;
    }

    public void cerrar(){
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Conexion con Cassandra Cerrada");
        }
    }


    


}   