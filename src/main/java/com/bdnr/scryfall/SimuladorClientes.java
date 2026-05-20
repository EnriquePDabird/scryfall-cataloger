package com.bdnr.scryfall;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SimuladorClientes {

    public enum PerfilCliente {
        TRADER,
        COLECCIONISTA,
        FOMO,
        ESPECULADOR
    }

    public static class RelojVirtual {
        private final java.time.ZonedDateTime inicio;
        private long segundosTranscurridos = 0;

        public RelojVirtual() {
            // Inicializar a las 09:00 del día actual
            this.inicio = java.time.ZonedDateTime.of(
                java.time.LocalDate.now(),
                java.time.LocalTime.of(9, 0),
                java.time.ZoneId.of("UTC")
            );
        }

        public synchronized java.time.Instant tick(int segundosAvanzar) {
            segundosTranscurridos += segundosAvanzar;
            java.time.ZonedDateTime fechaActual = inicio.plusSeconds(segundosTranscurridos);
            int hora = fechaActual.getHour();

            // Si nos pasamos de las 17:00, saltamos al día siguiente a las 09:00
            if (hora >= 17) {
                java.time.ZonedDateTime siguienteDiaNueve = fechaActual
                    .plusDays(1)
                    .withHour(9)
                    .withMinute(0)
                    .withSecond(0)
                    .withNano(0);
                long diffSegundos = java.time.Duration.between(inicio, siguienteDiaNueve).getSeconds();
                segundosTranscurridos = diffSegundos;
                fechaActual = siguienteDiaNueve;
            }
            return fechaActual.toInstant();
        }

        public synchronized java.time.Instant getHoraActual() {
            return inicio.plusSeconds(segundosTranscurridos).toInstant();
        }

        public synchronized String getHoraFormateada() {
            java.time.ZonedDateTime fechaActual = inicio.plusSeconds(segundosTranscurridos);
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            return fechaActual.format(formatter);
        }
    }

    private final RepositorioCassandra db;
    private final int numClientes;
    private final int iteracionesPorCliente;
    private final java.util.function.Consumer<String> uiLogger;
    
    private final AtomicInteger peticionesCompletadas = new AtomicInteger(0);
    private final AtomicInteger cambiosPrecioMarketMaker = new AtomicInteger(0);
    private final AtomicInteger comprasSimuladas = new AtomicInteger(0);
    private final AtomicInteger ventasSimuladas = new AtomicInteger(0);
    
    // Contadores por perfil
    private final AtomicInteger comprasTrader = new AtomicInteger(0);
    private final AtomicInteger ventasTrader = new AtomicInteger(0);
    private final AtomicInteger comprasColeccionista = new AtomicInteger(0);
    private final AtomicInteger ventasColeccionista = new AtomicInteger(0);
    private final AtomicInteger comprasFomo = new AtomicInteger(0);
    private final AtomicInteger ventasFomo = new AtomicInteger(0);
    private final AtomicInteger comprasEspeculador = new AtomicInteger(0);
    private final AtomicInteger ventasEspeculador = new AtomicInteger(0);

    private final RelojVirtual reloj = new RelojVirtual();
    private volatile boolean simulacionActiva = true;

    public SimuladorClientes(RepositorioCassandra db, int numClientes, int iteracionesPorCliente) {
        this(db, numClientes, iteracionesPorCliente, System.out::println);
    }

    public SimuladorClientes(RepositorioCassandra db, int numClientes, int iteracionesPorCliente, java.util.function.Consumer<String> uiLogger) {
        this.db = db;
        this.numClientes = numClientes;
        this.iteracionesPorCliente = iteracionesPorCliente;
        this.uiLogger = uiLogger;
    }

    public void iniciarSimulacion() {
        uiLogger.accept("🚀 Iniciando Simulador de Clientes...");
        uiLogger.accept("👥 Clientes concurrentes: " + numClientes);
        uiLogger.accept("🔄 Peticiones por cliente: " + iteracionesPorCliente);

        System.out.println("🚀 Iniciando Simulador de Clientes (Load Testing)...");
        System.out.println("👥 Clientes concurrentes: " + numClientes);
        System.out.println("🔄 Peticiones por cliente: " + iteracionesPorCliente);

        ExecutorService executor = Executors.newFixedThreadPool(numClientes);
        long startTime = System.currentTimeMillis();

        // Obtenemos cajas existentes para hacer consultas más realistas
        List<String> cajas = db.obtenerNombresCajas();
        if (cajas.isEmpty()) {
            cajas.add("Caja 1");
        }
        String[] edicionesPopulares = {"lea", "arn", "atq", "leg", "drk", "fem", "ice", "hml", "all", "mir"};

        // Sembrar catálogo sin stock
        sembrarCatalogoSinStock();

        // Preparación Market Maker
        java.util.Map<java.util.UUID, Double> cartasBase = new java.util.concurrent.ConcurrentHashMap<>(db.obtenerTodasLasCartasConPrecio());
        java.util.List<java.util.UUID> listaIdsCartas = new java.util.ArrayList<>(cartasBase.keySet());
        
        // Sembrar historial de precios inicial
        if (!listaIdsCartas.isEmpty()) {
            System.out.println("🌱 Sembrando historial de precios inicial...");
            uiLogger.accept("🌱 Sembrando historial de precios inicial...");
            Random rm = new Random();
            long ahora = System.currentTimeMillis();
            
            // Sembrar historial de precios (5 puntos históricos para cada carta)
            for (java.util.UUID cartaId : listaIdsCartas) {
                double precioBase = cartasBase.getOrDefault(cartaId, 0.0);
                for (int h = 5; h >= 1; h--) {
                    double fluctuacion = 0.95 + (rm.nextDouble() * 0.10); // +/- 5%
                    db.registrarCambioPrecioConFecha(cartaId, precioBase * fluctuacion, java.time.Instant.ofEpochMilli(ahora - h * 10000));
                }
            }
        }
        
        Thread marketMaker = new Thread(() -> {
            Random rm = new Random();
            long ultEvent = System.currentTimeMillis();
            while (simulacionActiva && !listaIdsCartas.isEmpty()) {
                try {
                    java.util.UUID cartaId = listaIdsCartas.get(rm.nextInt(listaIdsCartas.size()));
                    double precioActual = cartasBase.getOrDefault(cartaId, 0.0);
                    // Fluctuación aleatoria entre -3% y +3%
                    double fluctuacion = 1.0 + (rm.nextDouble() * 0.06 - 0.03); 
                    double nuevoPrecio = precioActual * fluctuacion;
                    cartasBase.put(cartaId, nuevoPrecio); // Actualizar base para el siguiente tick
                    
                    java.time.Instant horaSimulada = reloj.getHoraActual();
                    db.registrarCambioPrecioConFecha(cartaId, nuevoPrecio, horaSimulada);
                    cambiosPrecioMarketMaker.incrementAndGet();

                    // --- SHOCKS DE MERCADO (Cada 15 segundos reales) ---
                    long ahoraReal = System.currentTimeMillis();
                    if (ahoraReal - ultEvent > 15000) {
                        ultEvent = ahoraReal;
                        inyectarShockDeMercado(cartasBase, listaIdsCartas, horaSimulada);
                    }

                    Thread.sleep(100);
                } catch (Exception e) {
                    System.err.println("Error en Market Maker: " + e.getMessage());
                }
            }
        });
        marketMaker.start();

        // Lanzar clientes concurrentes
        for (int i = 0; i < numClientes; i++) {
            final int clienteId = i;
            executor.submit(() -> {
                Random random = new Random();
                // Asignar perfil aleatorio a este cliente
                PerfilCliente perfil = PerfilCliente.values()[random.nextInt(PerfilCliente.values().length)];

                for (int j = 0; j < iteracionesPorCliente; j++) {
                    try {
                        int accion = random.nextInt(4);
                        switch (accion) {
                            case 0:
                                // Leer precio de una carta al azar
                                if (!listaIdsCartas.isEmpty()) {
                                    java.util.UUID cartaLectura = listaIdsCartas.get(random.nextInt(listaIdsCartas.size()));
                                    db.obtenerHistorialReciente(cartaLectura, 5);
                                    reloj.tick(random.nextInt(30) + 10); // Avanzar 10-40 segundos virtuales
                                }
                                break;
                            case 1:
                                // Consultar cartas de una edición popular
                                String edicion = edicionesPopulares[random.nextInt(edicionesPopulares.length)];
                                db.consultaSilenciosaPorEdicion(edicion);
                                reloj.tick(random.nextInt(60) + 30); // Avanzar 30-90 segundos virtuales
                                break;
                            case 2:
                                // Simular venta por parte de un cliente (vende a la tienda, incrementa inventario)
                                if (!listaIdsCartas.isEmpty() && !cajas.isEmpty()) {
                                    java.util.UUID cartaVenta = listaIdsCartas.get(random.nextInt(listaIdsCartas.size()));
                                    double precioActual = cartasBase.getOrDefault(cartaVenta, 0.0);
                                    
                                    java.time.Instant horaTransaccion = reloj.tick(random.nextInt(300) + 60); // Avanzar 1-6 minutos virtuales
                                    boolean decidirVender = decidirVentaPorPerfil(perfil, cartaVenta, precioActual);
                                    
                                    if (decidirVender) {
                                        String caja = cajas.get(random.nextInt(cajas.size()));
                                        CartaMtg carta = db.obtenerCartaPorId(cartaVenta);
                                        if (carta != null) {
                                            String precioStr = String.format(java.util.Locale.US, "%.2f", precioActual);
                                            db.modificarStock(caja, carta.getId(), carta.getName(), carta.getSet_name(), precioStr, 1);
                                            db.registrarTransaccionConFecha(carta.getId(), precioActual, "venta", horaTransaccion);
                                            ventasSimuladas.incrementAndGet();
                                            incrementarVentasPerfil(perfil);
                                        }
                                    }
                                }
                                break;
                            case 3: {
                                // Simular compra por parte de un cliente (compra a la tienda, decrementa inventario)
                                if (!cajas.isEmpty()) {
                                    boolean compraDeCatalogoGeneral = random.nextInt(10) < 3; // 30% de probabilidad de pedir del catálogo general
                                    java.util.UUID cartaId = null;
                                    String cajaElegida = null;
                                    String nombreCarta = null;
                                    String edicionCarta = null;
                                    int stockActual = 0;
                                    double precioActual = 0.0;

                                    if (compraDeCatalogoGeneral) {
                                        java.util.List<java.util.UUID> cartasSinStock = db.obtenerCartasSinStock();
                                        if (!cartasSinStock.isEmpty()) {
                                            cartaId = cartasSinStock.get(random.nextInt(cartasSinStock.size()));
                                        } else if (!listaIdsCartas.isEmpty()) {
                                            cartaId = listaIdsCartas.get(random.nextInt(listaIdsCartas.size()));
                                        }

                                        if (cartaId != null) {
                                            // Buscar en qué caja está la carta y qué stock tiene
                                            cajaElegida = db.buscarCajaDeCarta(cartaId);
                                            CartaMtg carta = db.obtenerCartaPorId(cartaId);
                                            if (carta != null) {
                                                nombreCarta = carta.getName();
                                                edicionCarta = carta.getSet_name();
                                                precioActual = cartasBase.getOrDefault(cartaId, 0.0);
                                                if (precioActual == 0.0 && carta.getEur_price() != null) {
                                                    try { precioActual = Double.parseDouble(carta.getEur_price()); } catch (Exception e) {}
                                                }
                                            
                                                if (cajaElegida != null) {
                                                    java.util.List<RepositorioCassandra.ItemInventario> itemsCaja = db.obtenerItemsCaja(cajaElegida);
                                                    for (RepositorioCassandra.ItemInventario it : itemsCaja) {
                                                        if (it.cartaId.equals(cartaId)) {
                                                            stockActual = it.cantidad;
                                                            break;
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        cajaElegida = cajas.get(random.nextInt(cajas.size()));
                                        java.util.List<RepositorioCassandra.ItemInventario> items = db.obtenerItemsCaja(cajaElegida);
                                        if (!items.isEmpty()) {
                                            RepositorioCassandra.ItemInventario item = items.get(random.nextInt(items.size()));
                                            cartaId = item.cartaId;
                                            nombreCarta = item.nombreCarta;
                                            edicionCarta = item.edicion;
                                            stockActual = item.cantidad;
                                            precioActual = cartasBase.getOrDefault(cartaId, 0.0);
                                            if (precioActual == 0.0 && item.precioEur != null) {
                                                try { precioActual = Double.parseDouble(item.precioEur); } catch (Exception e) {}
                                            }
                                        }
                                    }

                                    if (cartaId != null && nombreCarta != null && edicionCarta != null) {
                                        java.time.Instant horaTransaccion = reloj.tick(random.nextInt(300) + 60); // Avanzar 1-6 minutos virtuales
                                        boolean decidirComprar = decidirCompraPorPerfil(perfil, cartaId, precioActual);

                                        if (decidirComprar) {
                                            if (cajaElegida == null || stockActual <= 0) {
                                                // PEDIDO DE EMERGENCIA
                                                cajaElegida = cajas.get(random.nextInt(cajas.size()));
                                                double costoMayoreo = precioActual * 0.70;
                                                double costoTotal = costoMayoreo * 5;
                                                db.registrarMovimientoBalanceConFecha(-costoTotal, "reabastecimiento", horaTransaccion);
                                                db.modificarStock(cajaElegida, cartaId, nombreCarta, edicionCarta, String.format(java.util.Locale.US, "%.2f", precioActual), 5);
                                                
                                                String msgEmergencia = "🚨 [EMERGENCIA] Cliente solicita '" + nombreCarta + "' sin stock. Pedido de emergencia de 5 uds a €" + String.format(java.util.Locale.US, "%.2f", costoTotal) + " (€" + String.format(java.util.Locale.US, "%.2f", costoMayoreo) + "/ud) para caja [" + cajaElegida + "].";
                                                uiLogger.accept(msgEmergencia);
                                                System.out.println(msgEmergencia);
                                                
                                                db.modificarStock(cajaElegida, cartaId, nombreCarta, edicionCarta, String.format(java.util.Locale.US, "%.2f", precioActual), -1);
                                            } else if (stockActual <= 1) {
                                                // REPOSTAJE AUTOMÁTICO ESTÁNDAR
                                                double costoMayoreo = precioActual * 0.70;
                                                double costoTotal = costoMayoreo * 10;
                                                db.registrarMovimientoBalanceConFecha(-costoTotal, "reabastecimiento", horaTransaccion);
                                                db.modificarStock(cajaElegida, cartaId, nombreCarta, edicionCarta, String.format(java.util.Locale.US, "%.2f", precioActual), 10);
                                                
                                                String msgReponer = "🛒 [REPOSTAJE] Tienda compra 10x '" + nombreCarta + "' por €" + String.format(java.util.Locale.US, "%.2f", costoTotal) + " para caja [" + cajaElegida + "].";
                                                uiLogger.accept(msgReponer);
                                                System.out.println(msgReponer);
                                            } else {
                                                db.modificarStock(cajaElegida, cartaId, nombreCarta, edicionCarta, String.format(java.util.Locale.US, "%.2f", precioActual), -1);
                                            }

                                            db.registrarTransaccionConFecha(cartaId, precioActual, "compra", horaTransaccion);
                                            comprasSimuladas.incrementAndGet();
                                            incrementarComprasPerfil(perfil);
                                        }
                                    }
                                }
                                break;
                            }
                        }
                        peticionesCompletadas.incrementAndGet();
                        Thread.sleep(random.nextInt(50));
                    } catch (Exception e) {
                        System.err.println("Error en Cliente " + clienteId + ": " + e.getMessage());
                    }
                }
            });
        }

        executor.shutdown();
        try {
            while (!executor.isTerminated()) {
                Thread.sleep(1000);
                String progreso = "📊 Progreso: " + peticionesCompletadas.get() + " / " + (numClientes * iteracionesPorCliente) + " peticiones | Hora virtual: " + reloj.getHoraFormateada();
                System.out.println(progreso);
                uiLogger.accept(progreso);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        simulacionActiva = false; // Detener Market Maker
        if (marketMaker.isAlive()) {
            try { marketMaker.join(2000); } catch (InterruptedException e) {}
        }

        long endTime = System.currentTimeMillis();
        long duracionMs = endTime - startTime;
        int totalPeticiones = numClientes * iteracionesPorCliente;
        double throughput = (double) totalPeticiones / (duracionMs / 1000.0);

        System.out.println("=========================================");
        System.out.println("✅ Simulación completada en " + duracionMs + " ms.");
        System.out.println("⚡ Throughput (Consultas Cliente): " + String.format("%.2f", throughput) + " req/seg");
        System.out.println("📈 Cambios de Precio inyectados: " + cambiosPrecioMarketMaker.get());
        System.out.println("🔴 Compras de clientes: " + comprasSimuladas.get());
        System.out.println("⚪ Ventas a la tienda: " + ventasSimuladas.get());
        System.out.println("=========================================");

        uiLogger.accept("=========================================");
        uiLogger.accept("✅ Simulación completada. Tiempo: " + duracionMs + " ms");
        uiLogger.accept("⚡ Throughput: " + String.format("%.2f", throughput) + " req/seg");
        uiLogger.accept("🔴 Total Compras: " + comprasSimuladas.get() + 
                        " (TRADER: " + comprasTrader.get() + 
                        ", COLECCIONISTA: " + comprasColeccionista.get() + 
                        ", FOMO: " + comprasFomo.get() + 
                        ", ESPECULADOR: " + comprasEspeculador.get() + ")");
        uiLogger.accept("⚪ Total Ventas: " + ventasSimuladas.get() + 
                        " (TRADER: " + ventasTrader.get() + 
                        ", COLECCIONISTA: " + ventasColeccionista.get() + 
                        ", FOMO: " + ventasFomo.get() + 
                        ", ESPECULADOR: " + ventasEspeculador.get() + ")");
        uiLogger.accept("💰 Balance Final de Tienda: €" + String.format(java.util.Locale.US, "%.2f", db.obtenerBalanceActual()));
        uiLogger.accept("=========================================");
    }

    private void inyectarShockDeMercado(java.util.Map<java.util.UUID, Double> cartasBase, java.util.List<java.util.UUID> listaIdsCartas, java.time.Instant horaSimulada) {
        if (listaIdsCartas.isEmpty()) return;
        Random rm = new Random();
        java.util.UUID cartaId = listaIdsCartas.get(rm.nextInt(listaIdsCartas.size()));
        
        CartaMtg carta = db.obtenerCartaPorId(cartaId);
        if (carta == null) return;

        double precioBase = cartasBase.getOrDefault(cartaId, 1.0);
        int tipoEvento = rm.nextInt(3);
        String mensaje = "";
        double nuevoPrecio = precioBase;

        switch (tipoEvento) {
            case 0:
                nuevoPrecio = precioBase * 0.50;
                mensaje = "⚠️ [EVENTO] Baneo de la carta '" + carta.getName() + "' en Commander! Precio baja 50% a €" + String.format(java.util.Locale.US, "%.2f", nuevoPrecio);
                break;
            case 1:
                nuevoPrecio = precioBase * 2.0;
                mensaje = "🏆 [EVENTO] '" + carta.getName() + "' gana el Pro Tour! El precio se duplica a €" + String.format(java.util.Locale.US, "%.2f", nuevoPrecio);
                break;
            case 2:
                nuevoPrecio = precioBase * 0.80;
                mensaje = "📦 [EVENTO] Reimpresión de '" + carta.getName() + "'! Aumenta oferta, precio baja 20% a €" + String.format(java.util.Locale.US, "%.2f", nuevoPrecio);
                break;
        }

        cartasBase.put(cartaId, nuevoPrecio);
        db.registrarCambioPrecioConFecha(cartaId, nuevoPrecio, horaSimulada);
        uiLogger.accept(mensaje);
        System.out.println(mensaje);
    }

    private boolean decidirCompraPorPerfil(PerfilCliente perfil, java.util.UUID cartaId, double precioActual) {
        java.util.List<Double> historial = db.obtenerHistorialReciente(cartaId, 5);
        if (historial.size() < 2) {
            return new Random().nextBoolean();
        }
        double suma = 0;
        for (double p : historial) suma += p;
        double promedio = suma / historial.size();

        switch (perfil) {
            case TRADER:
                return precioActual < promedio;
            case COLECCIONISTA:
                return new Random().nextBoolean();
            case FOMO:
                return precioActual > promedio;
            case ESPECULADOR:
                return precioActual < promedio * 0.99;
            default:
                return false;
        }
    }

    private boolean decidirVentaPorPerfil(PerfilCliente perfil, java.util.UUID cartaId, double precioActual) {
        if (perfil == PerfilCliente.COLECCIONISTA) {
            return false;
        }
        java.util.List<Double> historial = db.obtenerHistorialReciente(cartaId, 5);
        if (historial.size() < 2) {
            return new Random().nextBoolean();
        }
        double suma = 0;
        for (double p : historial) suma += p;
        double promedio = suma / historial.size();

        switch (perfil) {
            case TRADER:
                return precioActual > promedio;
            case FOMO:
                return precioActual < promedio;
            case ESPECULADOR:
                return precioActual > promedio * 1.01;
            default:
                return false;
        }
    }

    private void incrementarComprasPerfil(PerfilCliente perfil) {
        switch (perfil) {
            case TRADER: comprasTrader.incrementAndGet(); break;
            case COLECCIONISTA: comprasColeccionista.incrementAndGet(); break;
            case FOMO: comprasFomo.incrementAndGet(); break;
            case ESPECULADOR: comprasEspeculador.incrementAndGet(); break;
        }
    }

    private void incrementarVentasPerfil(PerfilCliente perfil) {
        switch (perfil) {
            case TRADER: ventasTrader.incrementAndGet(); break;
            case COLECCIONISTA: ventasColeccionista.incrementAndGet(); break;
            case FOMO: ventasFomo.incrementAndGet(); break;
            case ESPECULADOR: ventasEspeculador.incrementAndGet(); break;
        }
    }

    public int getComprasSimuladas() { return comprasSimuladas.get(); }
    public int getVentasSimuladas() { return ventasSimuladas.get(); }
    public int getPeticionesCompletadas() { return peticionesCompletadas.get(); }
    public String getHoraVirtualFormateada() { return reloj.getHoraFormateada(); }

    private void sembrarCatalogoSinStock() {
        String[][] cartasCatalogo = {
            {"56461c2b-cb18-472d-88b9-e14b1c7bfbb0", "Black Lotus", "vma", "20000.00"},
            {"e4479532-6a68-45e0-8a49-411a766c8c4a", "Mox Sapphire", "vma", "6000.00"},
            {"a5438848-356c-48d6-96a9-0db94ff8a385", "Ancestral Recall", "vma", "3500.00"},
            {"0720412e-a34f-4d9a-b4ff-579fbfe280a5", "Time Walk", "vma", "4000.00"},
            {"45d7a64c-bdf6-43b0-b5a0-9b634629d682", "Lightning Bolt", "a25", "2.50"},
            {"ddb17e4f-6d27-4a0b-932d-96e06b322a33", "Counterspell", "a25", "1.50"},
            {"f4b7a421-4f32-44a3-868d-8d5f303f27de", "Force of Will", "ema", "75.00"},
            {"d6cd20e0-47b8-4c12-a720-4a87754f9a46", "Brainstorm", "a25", "1.20"},
            {"a6b10f27-be2d-45db-b952-65626cc9a12c", "Swords to Plowshares", "a25", "2.00"},
            {"b4db0e68-07e5-4a57-8973-c64dc78ef9c7", "Demonic Tutor", "uma", "40.00"}
        };

        for (String[] datosCarta : cartasCatalogo) {
            java.util.UUID id = java.util.UUID.fromString(datosCarta[0]);
            CartaMtg existente = db.obtenerCartaPorId(id);
            if (existente == null) {
                CartaMtg nueva = new CartaMtg();
                nueva.setId(id);
                nueva.setName(datosCarta[1]);
                nueva.setSet_name(datosCarta[2]);
                nueva.setEur_price(datosCarta[3]);
                nueva.setUsd_price(datosCarta[3]);
                db.guardarCarta(nueva, true);
            }
        }
    }
}
