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
    private final PreparedStatement insertTransaccion;
    private final PreparedStatement selectTransacciones;
    private final PreparedStatement insertBalance;
    private final PreparedStatement selectLatestBalance;
    private final java.util.concurrent.atomic.AtomicReference<Double> balanceEnMemoria = new java.util.concurrent.atomic.AtomicReference<>(10000.0);

    public static class PuntoHistorico {
        public final java.time.Instant fecha;
        public final double precio;
        public final String tipo;
        public PuntoHistorico(java.time.Instant fecha, double precio, String tipo) {
            this.fecha = fecha; this.precio = precio; this.tipo = tipo;
        }
    }

    public static class DatosGrafica {
        public java.util.List<PuntoHistorico> historial = new java.util.ArrayList<>();
        public java.util.List<PuntoHistorico> transacciones = new java.util.ArrayList<>();
    }

    public static class ItemInventario {
        public final java.util.UUID cartaId;
        public final String nombreCarta;
        public final String edicion;
        public final String precioEur;
        public final int cantidad;
        public ItemInventario(java.util.UUID cartaId, String nombreCarta, String edicion, String precioEur, int cantidad) {
            this.cartaId = cartaId;
            this.nombreCarta = nombreCarta;
            this.edicion = edicion;
            this.precioEur = precioEur;
            this.cantidad = cantidad;
        }
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

        this.insertTransaccion = session.prepare(
            "INSERT INTO transacciones (carta_id, fecha, tipo, precio_eur) VALUES (?, ?, ?, ?)"
        );

        this.selectTransacciones = session.prepare(
            "SELECT fecha, tipo, precio_eur FROM transacciones WHERE carta_id = ? LIMIT 100"
        );

        this.insertBalance = session.prepare(
            "INSERT INTO balance_tienda (tienda_id, fecha, balance, tipo_operacion, monto) VALUES (?, ?, ?, ?, ?)"
        );

        this.selectLatestBalance = session.prepare(
            "SELECT balance FROM balance_tienda WHERE tienda_id = ? LIMIT 1"
        );

        // Cargar balance actual en memoria en la inicialización
        try {
            ResultSet rsBal = session.execute(selectLatestBalance.bind("principal"));
            Row filaBal = rsBal.one();
            if (filaBal != null) {
                balanceEnMemoria.set(filaBal.getDouble("balance"));
            } else {
                balanceEnMemoria.set(10000.0);
                session.execute(insertBalance.bind("principal", java.time.Instant.now(), 10000.0, "inicial", 10000.0));
            }
        } catch (Exception e) {
            System.err.println("⚠️ Error cargando balance inicial de la base de datos: " + e.getMessage());
            balanceEnMemoria.set(10000.0);
        }

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

    public void registrarCambioPrecioConFecha(java.util.UUID cartaId, double nuevoPrecio, java.time.Instant fecha) {
        session.execute(insertHistorialPrecio.bind(cartaId, fecha, nuevoPrecio));
    }

    public void limpiarTransacciones() {
        session.execute("TRUNCATE transacciones");
    }

    public void limpiarBalance() {
        session.execute("TRUNCATE balance_tienda");
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

    public void registrarTransaccion(java.util.UUID cartaId, double precio, String tipo) {
        registrarTransaccionConFecha(cartaId, precio, tipo, java.time.Instant.now());
    }

    public void registrarTransaccionConFecha(java.util.UUID cartaId, double precio, String tipo, java.time.Instant fecha) {
        session.execute(insertTransaccion.bind(cartaId, fecha, tipo, precio));
        
        // Registrar el impacto en el balance de la tienda
        double cambioBalance = tipo.equals("compra") ? precio : -precio;
        registrarMovimientoBalanceConFecha(cambioBalance, tipo.equals("compra") ? "compra_cliente" : "venta_cliente", fecha);
    }

    public double obtenerBalanceActual() {
        return balanceEnMemoria.get();
    }

    public synchronized void registrarMovimientoBalanceConFecha(double monto, String tipoOperacion, java.time.Instant fecha) {
        double nuevoBalance = balanceEnMemoria.get() + monto;
        balanceEnMemoria.set(nuevoBalance);
        
        // Usamos la hora de inserción real o un timestamp que incremente estrictamente 
        // para garantizar que la clave principal (tienda_id, fecha) sea única y no se pisen.
        java.time.Instant ahora = java.time.Instant.now();
        session.execute(insertBalance.bind("principal", ahora, nuevoBalance, tipoOperacion, monto));
    }

    public DatosGrafica obtenerDatosGrafica(java.util.UUID cartaId) {
        DatosGrafica datos = new DatosGrafica();
        // Leemos hasta 100 puntos históricos recientes (están en orden DESC por el schema)
        ResultSet res1 = session.execute(session.prepare("SELECT fecha, precio_eur FROM historial_precios WHERE carta_id = ? LIMIT 100").bind(cartaId));
        for(Row r : res1) {
            datos.historial.add(new PuntoHistorico(r.getInstant("fecha"), r.getDouble("precio_eur"), null));
        }
        
        ResultSet res2 = session.execute(selectTransacciones.bind(cartaId));
        for(Row r : res2) {
            datos.transacciones.add(new PuntoHistorico(r.getInstant("fecha"), r.getDouble("precio_eur"), r.getString("tipo")));
        }

        // Para graficar es mejor tenerlos en orden cronológico (de más antiguo a más nuevo)
        java.util.Collections.reverse(datos.historial);
        java.util.Collections.reverse(datos.transacciones);
        return datos;
    }

    public java.util.List<ItemInventario> obtenerItemsCaja(String nombreCaja) {
        java.util.List<ItemInventario> items = new java.util.ArrayList<>();
        ResultSet rs = session.execute(session.prepare("SELECT carta_id, nombre_carta, edicion, precio_eur, cantidad FROM inventario_por_caja WHERE nombre_caja = ?").bind(nombreCaja));
        for (Row fila : rs) {
            items.add(new ItemInventario(
                fila.getUuid("carta_id"),
                fila.getString("nombre_carta"),
                fila.getString("edicion"),
                fila.getString("precio_eur"),
                fila.getInt("cantidad")
            ));
        }
        return items;
    }

    public void modificarStock(String nombreCaja, java.util.UUID cartaId, String nombreCarta, String edicion, String precioEur, int cambio) {
        int cantidadActual = 0;
        ResultSet rs = session.execute(session.prepare("SELECT cantidad FROM inventario_por_caja WHERE nombre_caja = ? AND carta_id = ?").bind(nombreCaja, cartaId));
        Row fila = rs.one();
        if (fila != null) {
            cantidadActual = fila.getInt("cantidad");
        }

        int nuevaCantidad = cantidadActual + cambio;

        if (nuevaCantidad <= 0) {
            session.execute(session.prepare("DELETE FROM inventario_por_caja WHERE nombre_caja = ? AND carta_id = ?").bind(nombreCaja, cartaId));
        } else {
            session.execute(session.prepare("INSERT INTO inventario_por_caja (nombre_caja, carta_id, nombre_carta, edicion, precio_eur, cantidad) VALUES (?, ?, ?, ?, ?, ?)")
                .bind(nombreCaja, cartaId, nombreCarta, edicion, precioEur, nuevaCantidad));
        }
    }

    public CartaMtg obtenerCartaPorId(java.util.UUID id) {
        ResultSet rs = session.execute(session.prepare("SELECT id, name, set_name, eur_price FROM cartas WHERE id = ?").bind(id));
        Row fila = rs.one();
        if (fila != null) {
            CartaMtg carta = new CartaMtg();
            carta.setId(fila.getUuid("id"));
            carta.setName(fila.getString("name"));
            carta.setSet_name(fila.getString("set_name"));
            carta.setEur_price(fila.getString("eur_price"));
            return carta;
        }
        return null;
    }

    public String buscarCajaDeCarta(java.util.UUID cartaId) {
        ResultSet rs = session.execute(session.prepare("SELECT nombre_caja FROM inventario_por_caja WHERE carta_id = ? ALLOW FILTERING").bind(cartaId));
        Row fila = rs.one();
        return (fila != null) ? fila.getString("nombre_caja") : null;
    }

    public java.util.List<java.util.UUID> obtenerCartasSinStock() {
        java.util.Set<java.util.UUID> enStock = new java.util.HashSet<>();
        ResultSet rsStock = session.execute("SELECT carta_id FROM inventario_por_caja");
        for (Row r : rsStock) {
            enStock.add(r.getUuid("carta_id"));
        }
        
        java.util.List<java.util.UUID> sinStock = new java.util.ArrayList<>();
        ResultSet rsCartas = session.execute("SELECT id FROM cartas");
        for (Row r : rsCartas) {
            java.util.UUID id = r.getUuid("id");
            if (!enStock.contains(id)) {
                sinStock.add(id);
            }
        }
        return sinStock;
    }

    public void eliminarCaja(String nombreCaja) {
        session.execute(session.prepare("DELETE FROM inventario_por_caja WHERE nombre_caja = ?").bind(nombreCaja));
        System.out.println("📦 Caja [" + nombreCaja + "] eliminada por completo de Cassandra.");
    }

    public void cerrar(){
        if (session != null && !session.isClosed()) {
            session.close();
            System.out.println("Conexion con Cassandra Cerrada");
        }
    }
}