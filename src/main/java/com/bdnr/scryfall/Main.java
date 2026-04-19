package com.bdnr.scryfall;

import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Scanner;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class Main {

    public static void main(String[] args) {
        // Inicializamos nuestras herramientas principales
        RepositorioCassandra db = new RepositorioCassandra();
        ObjectMapper mapper = new ObjectMapper();
        HttpClient client = HttpClient.newHttpClient();
        Scanner scanner = new Scanner(System.in);

        try {
            boolean salir = false;

            // Bucle principal: El programa se repetirá hasta que salir sea true
            while (!salir) {
                System.out.println("\n=================================");
                System.out.println("   CATÁLOGO MAGIC: THE GATHERING");
                System.out.println("=================================");
                System.out.println("1. Buscar e insertar una carta nueva");
                System.out.println("2. Ver todas las cartas en Cassandra");
                System.out.println("3. Ver cartas por lote al que pertenecen");
                System.out.println("4. Descargar e inyectar BULK DATA completo"); // Texto actualizado
                System.out.println("5. Salir del programa");
                System.out.print("Elige una opción (1-5): "); // Actualizado a 1-5
                String opcion = scanner.nextLine();

                switch (opcion) {
                    case "1":
                        System.out.print("Introduce el nombre exacto de la carta (ej. Black Lotus): ");
                        String nombreCarta = scanner.nextLine();
                        buscarYGuardarCarta(nombreCarta, client, mapper, db, scanner); 
                        break;
                    
                    case "2":
                        System.out.println("\n--- INVENTARIO ACTUAL ---");
                        db.imprimirTodasLasCartas();
                        break;
                    
                    case "3":
                        System.out.print("Introduce el código de la edición (ej. 'soc' o 'spm'): ");
                        String edicionBusqueda = scanner.nextLine();
                        db.imprimirCartasPorEdicion(edicionBusqueda.toLowerCase());
                        break;
    
                    case "4":
                        descargarEInsertarBulk(client, mapper, db);
                        break;

                    case "5":
                        salir = true;
                        System.out.println("Cerrando el catálogo. ¡Hasta pronto!");
                        break;


                    default:
                        System.out.println("Opción no válida.");
                }
            }

        } catch (Exception e) {
            System.out.println("Ocurrió un error inesperado en el menú principal:");
            e.printStackTrace();
        } finally {
            scanner.close();
            db.cerrar();
        }
    }
    /**
     * Método auxiliar que busca TODAS las versiones y permite al usuario elegir.
     */
    private static void buscarYGuardarCarta(String nombreCarta, HttpClient client, ObjectMapper mapper, RepositorioCassandra db, Scanner scanner) {
        try {
            // 1. Sintaxis de Scryfall para nombre exacto: !"Nombre de la Carta"
            String queryBusqueda = "!\"" + nombreCarta + "\"";
            String busquedaCodificada = URLEncoder.encode(queryBusqueda, StandardCharsets.UTF_8.toString());

            // 2. Usamos el endpoint /cards/search con el modificador unique=prints
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.scryfall.com/cards/search?q=" + busquedaCodificada + "&unique=prints"))
                    .header("User-Agent", "CatalogoCartasMtg/1.0")
                    .header("Accept", "*/*")
                    .GET()
                    .build();

            System.out.println("🔍 Buscando todas las ediciones de '" + nombreCarta + "'...");
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // 3. Procesamos la respuesta
            if (response.statusCode() == 200) {
                // Convertimos el JSON general a nuestra clase envoltorio
                RespuestaScryfall respuesta = mapper.readValue(response.body(), RespuestaScryfall.class);
                
                // Extraemos la lista de cartas
                List<CartaMtg> versiones = respuesta.getData();

                System.out.println("\n✨ Se han encontrado " + versiones.size() + " versiones. Elige cuál quieres guardar:");
                
                // Imprimimos el menú con un bucle for
                for (int i = 0; i < versiones.size(); i++) {
                    CartaMtg carta = versiones.get(i);
                    // Mostramos el número, la edición (en mayúsculas) y el precio para ayudar a elegir
                    System.out.printf("  %d. Edición [%s] | USD: $%s | EUR: €%s%n", 
                            (i + 1), 
                            carta.getSet_name().toUpperCase(), 
                            carta.getUsd_price(), 
                            carta.getEur_price());
                }
                
                System.out.print("\nEscribe el número de la edición (0 para cancelar): ");
                
                try {
                    int eleccion = Integer.parseInt(scanner.nextLine());
                    
                    if (eleccion > 0 && eleccion <= versiones.size()) {
                        // Rescatamos la carta elegida de la lista (restamos 1 porque los arrays empiezan en 0)
                        CartaMtg cartaElegida = versiones.get(eleccion - 1);
                        db.guardarCarta(cartaElegida, false);
                    } else if (eleccion == 0) {
                        System.out.println("Operación cancelada.");
                    } else {
                        System.out.println("Número fuera de rango. Operación cancelada.");
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Entrada inválida. Debes escribir un número.");
                }
                
            } else if (response.statusCode() == 404) {
                System.out.println("Scryfall no encontró ninguna carta con el nombre exacto: " + nombreCarta);
            } else {
                System.out.println("Error de Scryfall. Código de respuesta: " + response.statusCode());
            }

        } catch (Exception e) {
            System.out.println("Error al intentar descargar o guardar la carta:");
            e.printStackTrace();
        }
    }

/**
     * Descarga el archivo Bulk de Scryfall de forma segura usando Streams 
     * e inyecta toda la base de datos en Cassandra.
     */
    private static void descargarEInsertarBulk(HttpClient client, ObjectMapper mapper, RepositorioCassandra db) {
        try {
            System.out.println("📡 1. Solicitando URL de descarga a Scryfall...");
            
            HttpRequest requestLink = HttpRequest.newBuilder()
                .uri(URI.create("https://api.scryfall.com/bulk-data/oracle-cards"))
                .header("User-Agent", "CatalogoCartasMtg/1.0")
                .header("Accept", "*/*") // <--- ¡ESTA ES LA QUE FALTA!
                .GET()
                .build();

            HttpResponse<String> responseLink = client.send(requestLink, HttpResponse.BodyHandlers.ofString());

            if (responseLink.statusCode() == 200) {
                // Obtenemos la URL secreta
                ScryfallBulk bulkInfo = mapper.readValue(responseLink.body(), ScryfallBulk.class);
                System.out.println("✅ URL obtenida. Iniciando descarga masiva...");
                System.out.println("⏳ Esto puede tardar varios minutos dependiendo de tu conexión...");

                HttpRequest requestData = HttpRequest.newBuilder()
                    .uri(URI.create(bulkInfo.getDownload_uri()))
                    .header("User-Agent", "CatalogoCartasMtg/1.0")
                    .header("Accept", "*/*") // <-- Y AÑADIR ESTA LÍNEA AQUÍ TAMBIÉN
                    .GET()
                    .build();

                // TRUCO DE RENDIMIENTO: Usamos ofInputStream() para no saturar la RAM
                HttpResponse<InputStream> responseData = client.send(requestData, HttpResponse.BodyHandlers.ofInputStream());

                System.out.println("🔄 Descarga en curso. Procesando el JSON sobre la marcha...");

                // Jackson leerá el InputStream (flujo de datos) en directo.
                List<CartaMtg> todasLasCartas = mapper.readValue(responseData.body(), new TypeReference<List<CartaMtg>>(){});

                System.out.println("📚 Se han detectado " + todasLasCartas.size() + " cartas únicas de Oracle. Iniciando inyección en Cassandra...");

                long tiempoInicio = System.currentTimeMillis();
                int contador = 0;

                // Inyectamos las cartas en Cassandra
                for (CartaMtg carta : todasLasCartas) {
                    // Guardamos la carta en modo silencioso (true)
                    db.guardarCarta(carta, true); 
                    contador++;

                    // Dibujamos una barra de progreso cada 5000 cartas
                    if (contador % 5000 == 0) {
                        System.out.println("   ⚡ Progreso: " + contador + " / " + todasLasCartas.size() + " cartas guardadas...");
                    }
                }

                long tiempoTotalSegundos = (System.currentTimeMillis() - tiempoInicio) / 1000;
                System.out.println("🎉 ¡ÉXITO! Base de datos sincronizada completamente.");
                System.out.println("⏱️ Tiempo total de inyección en Cassandra: " + tiempoTotalSegundos + " segundos.");

            } else {
                System.out.println("❌ Error al pedir el Bulk Data. Código: " + responseLink.statusCode());
            }

        } catch (Exception e) {
            System.out.println("💥 Error crítico durante la sincronización masiva:");
            e.printStackTrace();
        }
    }
}