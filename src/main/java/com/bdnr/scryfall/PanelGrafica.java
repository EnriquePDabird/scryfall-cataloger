package com.bdnr.scryfall;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PanelGrafica extends JPanel {
    private RepositorioCassandra.DatosGrafica datos;

    public void actualizarDatos(RepositorioCassandra.DatosGrafica datos) {
        this.datos = datos;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int ancho = getWidth();
        int alto = getHeight();
        int padding = 50;

        // Dibujar fondo oscuro para un look más moderno
        g2.setColor(new Color(30, 30, 30));
        g2.fillRect(0, 0, ancho, alto);

        if (datos == null || datos.historial.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.drawString("Aún no hay datos para graficar. Lanza el simulador primero.", ancho / 2 - 150, alto / 2);
            return;
        }

        // Encontrar precio mínimo y máximo
        double minPrecio = Double.MAX_VALUE;
        double maxPrecio = Double.MIN_VALUE;
        for (RepositorioCassandra.PuntoHistorico p : datos.historial) {
            if (p.precio < minPrecio) minPrecio = p.precio;
            if (p.precio > maxPrecio) maxPrecio = p.precio;
        }

        double rango = maxPrecio - minPrecio;
        if (rango == 0) rango = 1;
        minPrecio -= rango * 0.2;
        maxPrecio += rango * 0.2;
        rango = maxPrecio - minPrecio;

        // Dibujar ejes
        g2.setColor(Color.GRAY);
        g2.drawLine(padding, alto - padding, ancho - padding, alto - padding); // X
        g2.drawLine(padding, padding, padding, alto - padding); // Y

        // Dibujar línea del precio
        g2.setColor(new Color(0, 255, 127)); // Verde neón
        g2.setStroke(new BasicStroke(2));

        List<RepositorioCassandra.PuntoHistorico> puntos = datos.historial;
        int n = puntos.size();
        double pasoX = (double) (ancho - 2 * padding) / (n > 1 ? n - 1 : 1);

        for (int i = 0; i < n - 1; i++) {
            int x1 = padding + (int) (i * pasoX);
            int y1 = alto - padding - (int) (((puntos.get(i).precio - minPrecio) / rango) * (alto - 2 * padding));
            int x2 = padding + (int) ((i + 1) * pasoX);
            int y2 = alto - padding - (int) (((puntos.get(i + 1).precio - minPrecio) / rango) * (alto - 2 * padding));
            g2.drawLine(x1, y1, x2, y2);
        }

        // Dibujar compras FOMO (Puntos rojos)
        g2.setColor(new Color(255, 50, 50));
        int diametro = 10;
        
        long tiempoInicio = puntos.get(0).fecha.toEpochMilli();
        long tiempoFin = puntos.get(n - 1).fecha.toEpochMilli();
        long rangoTiempo = tiempoFin - tiempoInicio;

        for (RepositorioCassandra.PuntoHistorico fomo : datos.comprasFomo) {
            long t = fomo.fecha.toEpochMilli();
            if (t < tiempoInicio || t > tiempoFin || rangoTiempo == 0) continue;

            double proporcionX = (double) (t - tiempoInicio) / rangoTiempo;
            int x = padding + (int) (proporcionX * (ancho - 2 * padding));
            int y = alto - padding - (int) (((fomo.precio - minPrecio) / rango) * (alto - 2 * padding));
            
            g2.fillOval(x - diametro/2, y - diametro/2, diametro, diametro);
        }
        
        // Leyendas
        g2.setColor(new Color(0, 255, 127));
        g2.fillRect(ancho - 150, 20, 15, 15);
        g2.setColor(Color.WHITE);
        g2.drawString("Evolución de Precio", ancho - 125, 32);

        g2.setColor(new Color(255, 50, 50));
        g2.fillOval(ancho - 150, 45, 15, 15);
        g2.setColor(Color.WHITE);
        g2.drawString("Compra por FOMO", ancho - 125, 57);
    }
}
