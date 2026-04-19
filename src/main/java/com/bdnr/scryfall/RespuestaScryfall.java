package com.bdnr.scryfall;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RespuestaScryfall {
    
    private List<CartaMtg> data;

    public RespuestaScryfall() {}

    public List<CartaMtg> getData() {
        return data;
    }

    public void setData(List<CartaMtg> data) {
        this.data = data;
    }
}