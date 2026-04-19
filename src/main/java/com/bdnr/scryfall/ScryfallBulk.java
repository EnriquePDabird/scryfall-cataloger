package com.bdnr.scryfall;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ScryfallBulk {
    private String download_uri;

    public ScryfallBulk() {}

    public String getDownload_uri() { return download_uri; }
    public void setDownload_uri(String download_uri) { this.download_uri = download_uri; }
}