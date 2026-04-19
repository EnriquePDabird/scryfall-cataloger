package com.bdnr.scryfall;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;

@JsonIgnoreProperties(ignoreUnknown = true)
public class CartaMtg {

    private UUID id;
    private String name;
    
    @JsonProperty("set")
    private String set_name;
    
    private String mana_cost;
    private String type_line;
    private String oracle_text;
    private List<String> colors;
    
    private String usd_price;
    private String eur_price;

    public CartaMtg() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSet_name() { return set_name; }
    public void setSet_name(String set_name) { this.set_name = set_name; }

    public String getMana_cost() { return mana_cost; }
    public void setMana_cost(String mana_cost) { this.mana_cost = mana_cost; }

    public String getType_line() { return type_line; }
    public void setType_line(String type_line) { this.type_line = type_line; }

    public String getOracle_text() { return oracle_text; }
    public void setOracle_text(String oracle_text) { this.oracle_text = oracle_text; }

    public List<String> getColors() { return colors; }
    public void setColors(List<String> colors) { this.colors = colors; }

    public String getUsd_price() { return usd_price; }
    public void setUsd_price(String usd_price) { this.usd_price = usd_price; }

    public String getEur_price() { return eur_price; }
    public void setEur_price(String eur_price) { this.eur_price = eur_price; }

    // --- EL TRUCO DE JACKSON PARA DATOS ANIDADOS ---
    
    /* * Scryfall nos envía esto: "prices": { "usd": "42.99", "eur": "35.00" }
     * En lugar de crear una clase separada 'Prices', interceptamos el bloque "prices"
     * cuando Jackson lo lee, extraemos solo el valor "usd" y lo asignamos a nuestra variable.
     */
    @JsonSetter("prices")
    private void desempaquetarPrecios(Map<String, String> prices) {
        if (prices != null) {
            String usd = prices.get("usd");
            String eur = prices.get("eur");
            
            // Ahora comprobamos que no sea nulo Y que tampoco sea la palabra "null"
            this.usd_price = (usd != null && !usd.equals("null")) ? usd : "0.00";
            this.eur_price = (eur != null && !eur.equals("null")) ? eur : "0.00";
        } else {
            this.usd_price = "0.00";
            this.eur_price = "0.00";
        }
    }
}