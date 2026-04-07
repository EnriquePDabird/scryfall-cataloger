package com.bdnr.scryfall;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse; 

import com.fasterxml.jackson.databind.ObjectMapper; 

public class Main {
    public static void main(String[] args) {
        RepositorioCassandra db = new RepositorioCassandra();
        ObjectMapper mapper = new ObjectMapper();

        try {
            System.out.println("Conectando con Scryfall...");
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.scryfall.com/cards/named?exact=Sol+Ring"))
                    .header("User-Agent", "CatalogoCartasMtg/1.0")
                    .header("Accept", "*/*")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            // Exito de conexion
            if (response.statusCode() == 200){
                CartaMtg miCarta = mapper.readValue(response.body(), CartaMtg.class);
                db.guardarCarta(miCarta);
            } else {
                System.out.println("Error al buscar la carta. Scryfall respondió con código: " + response.statusCode());
            }
        } catch (Exception e) {
            System.out.println("Ocurrió un error inesperado:");
            e.printStackTrace();
        } finally{
            db.cerrar();
        }
    }
}