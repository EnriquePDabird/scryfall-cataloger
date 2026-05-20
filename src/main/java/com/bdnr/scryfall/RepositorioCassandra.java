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
    private final PreparedStatement insertHistorialPrecio;
    private final PreparedStatement selectHistorialPrecio;
    private final PreparedStatement insertCompraFomo;
    private final PreparedStatement selectComprasFomo;

    public static class PuntoHistorico {
        public final java.time.Instant fecha;
        public final double precio;
        public final boolean esFomo;
        public PuntoHistorico(java.time.Instant fecha, double precio, boolean esFomo) {
            this.fecha = fecha; this.precio = precio; this.esFomo = esFomo;
        }
    }

    public static class DatosGrafica {
        public java.util.List<PuntoHistorico> historial = new java.util.ArrayList<>();
        public java.util.List<PuntoHistorico> comprasFomo = new java.util.ArrayList<>();
    }



    public RepositorioCassandra() {
        this.session = CqlSession.builder()
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9042))
            .addContactPoint(new InetSocketAddress("127.0.0.1", 9043))
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

        this.insertHistorialPrecio = session.prepare(
            "INSERT INTO historial_precios (carta_id, fecha, precio_eur) VALUES (?, ?, ?)"
        );
        
        this.selectHistorialPrecio = session.prepare(
            "SELECT precio_eur FROM historial_precios WHERE carta_id = ? LIMIT ?"
        );

        this.insertCompraFomo = session.prepare(
            "INSERT INTO compras_fomo (carta_id, fecha, precio_eur) VALUES (?, ?, ?)"
        );

        this.selectComprasFomo = session.prepare(
            "SELECT fecha, precio_eur FROM compras_fomo WHERE carta_id = ? LIMIT 100"
        );

        System.out.println("Conectado a Cassandra. Repositorio listo.");
    }

    public void guardarCarta(CartaMtg carta, boolean silencioso) {
        
        String coloresStr = (carta.getColors() != null) ? String.join(", ", carta.getColors()) : "";

        BoundStatement statement1 = insertPrincipal.bind(
            carta.getId(), carta.getName(), carta.getSet_name(),
            carta.getMana_cost(), carta.getType_line(), carta.getOracle_text(),
            coloresStr, carta.getUsd_price(), carta.getEur_price()
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

    public void consultaSilenciosaPorEdicion(String edicion) {
        ResultSet resultados = session.execute("SELECT * FROM cartas_por_edicion WHERE set_name = '" + edicion + "'");
        for (Row fila : resultados) {
            // Solo iterar para consumir el iterador, simulando lectura en memoria
            fila.getString("name");
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

    public void registrarCambioPrecio(java.util.UUID cartaId, double nuevoPrecio) {
        session.execute(insertHistorialPrecio.bind(cartaId, java.time.Instant.now(), nuevoPrecio));
    }

    public java.util.List<Double> obtenerHistorialReciente(java.util.UUID cartaId, int limite) {
        java.util.List<Double> historial = new java.util.ArrayList<>();
        ResultSet resultados = session.execute(selectHistorialPrecio.bind(cartaId, limite));
        for (Row fila : resultados) {
            historial.add(fila.getDouble("precio_eur"));
        }
        return historial;
    }

    public java.util.Map<java.util.UUID, Double> obtenerTodasLasCartasConPrecio() {
        java.util.Map<java.util.UUID, Double> cartas = new java.util.HashMap<>();
        ResultSet resultados = session.execute("SELECT id, eur_price FROM cartas");
        for (Row fila : resultados) {
            java.util.UUID id = fila.getUuid("id");
            String precioStr = fila.getString("eur_price");
            if (precioStr != null && !precioStr.isEmpty() && !precioStr.equals("null")) {
                try {
                    cartas.put(id, Double.parseDouble(precioStr));
                } catch (NumberFormatException ignored) {}
            }
        }
        return cartas;
    }

    public java.util.Map<java.util.UUID, String> obtenerNombresCartas() {
        java.util.Map<java.util.UUID, String> nombres = new java.util.HashMap<>();
        ResultSet resultados = session.execute("SELECT id, name FROM cartas");
        for (Row fila : resultados) {
            nombres.put(fila.getUuid("id"), fila.getString("name"));
        }
        return nombres;
    }

    public void registrarCompraFomo(java.util.UUID cartaId, double precio) {
        session.execute(insertCompraFomo.bind(cartaId, java.time.Instant.now(), precio));
    }

    public DatosGrafica obtenerDatosGrafica(java.util.UUID cartaId) {
        DatosGrafica datos = new DatosGrafica();
        // Leemos hasta 100 puntos históricos recientes (están en orden DESC por el schema)
        ResultSet res1 = session.execute(session.prepare("SELECT fecha, precio_eur FROM historial_precios WHERE carta_id = ? LIMIT 100").bind(cartaId));
        for(Row r : res1) {
            datos.historial.add(new PuntoHistorico(r.getInstant("fecha"), r.getDouble("precio_eur"), false));
        }
        
        ResultSet res2 = session.execute(selectComprasFomo.bind(cartaId));
        for(Row r : res2) {
            datos.comprasFomo.add(new PuntoHistorico(r.getInstant("fecha"), r.getDouble("precio_eur"), true));
        }

        // Para graficar es mejor tenerlos en orden cronológico (de más antiguo a más nuevo)
        java.util.Collections.reverse(datos.historial);
        java.util.Collections.reverse(datos.comprasFomo);
        return datos;
    }

    public void cerrar(){
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Conexion con Cassandra Cerrada");
        }
    }


    


}   