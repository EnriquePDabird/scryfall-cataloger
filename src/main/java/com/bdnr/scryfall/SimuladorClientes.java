package com.bdnr.scryfall;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

public class SimuladorClientes {

    private final RepositorioCassandra db;
    private final int numClientes;
    private final int iteracionesPorCliente;
    private final AtomicInteger peticionesCompletadas = new AtomicInteger(0);
    private final AtomicInteger cambiosPrecioMarketMaker = new AtomicInteger(0);
    private final AtomicInteger comprasFomo = new AtomicInteger(0);
    private volatile boolean simulacionActiva = true;

    public SimuladorClientes(RepositorioCassandra db, int numClientes, int iteracionesPorCliente) {
        this.db = db;
        this.numClientes = numClientes;
        this.iteracionesPorCliente = iteracionesPorCliente;
    }

    public void iniciarSimulacion() {
        System.out.println("🚀 Iniciando Simulador de Clientes (Load Testing)...");
        System.out.println("👥 Clientes concurrentes: " + numClientes);
        System.out.println("🔄 Peticiones por cliente: " + iteracionesPorCliente);

        ExecutorService executor = Executors.newFixedThreadPool(numClientes);
        long startTime = System.currentTimeMillis();

        // Obtenemos cajas existentes para hacer consultas más realistas
        List<String> cajas = db.obtenerNombresCajas();
        String[] edicionesPopulares = {"lea", "arn", "atq", "leg", "drk", "fem", "ice", "hml", "all", "mir"};

        // Preparación Market Maker
        java.util.Map<java.util.UUID, Double> cartasBase = db.obtenerTodasLasCartasConPrecio();
        java.util.List<java.util.UUID> listaIdsCartas = new java.util.ArrayList<>(cartasBase.keySet());
        
        Thread marketMaker = new Thread(() -> {
            Random rm = new Random();
            while (simulacionActiva && !listaIdsCartas.isEmpty()) {
                java.util.UUID cartaId = listaIdsCartas.get(rm.nextInt(listaIdsCartas.size()));
                double precioActual = cartasBase.get(cartaId);
                // Fluctuación aleatoria entre -3% y +3%
                double fluctuacion = 1.0 + (rm.nextDouble() * 0.06 - 0.03); 
                double nuevoPrecio = precioActual * fluctuacion;
                cartasBase.put(cartaId, nuevoPrecio); // Actualizar base para el siguiente tick
                
                db.registrarCambioPrecio(cartaId, nuevoPrecio);
                cambiosPrecioMarketMaker.incrementAndGet();
                try { Thread.sleep(5); } catch (InterruptedException e) {}
            }
        });
        
        if (!listaIdsCartas.isEmpty()) {
            marketMaker.start();
        } else {
            System.out.println("⚠️ Market Maker no pudo iniciar: No hay cartas con precio en la BD.");
        }

        for (int i = 0; i < numClientes; i++) {
            final int clienteId = i + 1;
            executor.submit(() -> {
                Random random = new Random();
                for (int j = 0; j < iteracionesPorCliente; j++) {
                    int accion = random.nextInt(4);
                    try {
                        switch (accion) {
                            case 0:
                                // Simular consulta de catálogo por edición
                                String edicion = edicionesPopulares[random.nextInt(edicionesPopulares.length)];
                                db.consultaSilenciosaPorEdicion(edicion);
                                break;
                            case 1:
                                // Simular arqueo de caja aleatoria
                                if (!cajas.isEmpty()) {
                                    String caja = cajas.get(random.nextInt(cajas.size()));
                                    db.obtenerEstadisticasCaja(caja); // Genera el reporte internamente sin imprimir
                                }
                                break;
                            case 2:
                                // Simular petición de listado de cajas
                                db.obtenerNombresCajas();
                                break;
                            case 3:
                                // Lógica FOMO: Analizar historial de precio y comprar
                                if (!listaIdsCartas.isEmpty()) {
                                    java.util.UUID cartaFomo = listaIdsCartas.get(random.nextInt(listaIdsCartas.size()));
                                    java.util.List<Double> historial = db.obtenerHistorialReciente(cartaFomo, 5);
                                    if (historial.size() >= 3) {
                                        // Si los 3 últimos precios van en aumento: precio0 > precio1 > precio2
                                        if (historial.get(0) > historial.get(1) && historial.get(1) > historial.get(2)) {
                                            db.registrarCompraFomo(cartaFomo, historial.get(0));
                                            comprasFomo.incrementAndGet();
                                        }
                                    }
                                }
                                break;
                        }
                        peticionesCompletadas.incrementAndGet();
                        // Pequeña pausa para simular latencia de red/think-time del usuario
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
                System.out.println("📊 Progreso: " + peticionesCompletadas.get() + " / " + (numClientes * iteracionesPorCliente) + " peticiones...");
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
        System.out.println("🤑 Compras por Pánico (FOMO): " + comprasFomo.get());
        System.out.println("=========================================");
    }
}
