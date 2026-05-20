package com.bdnr.scryfall;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class PanelGrafica extends JPanel {
    private RepositorioCassandra.DatosGrafica datos;
    private RepositorioCassandra.PuntoHistorico hoveredPoint = null;
    private Point mousePos = null;

    public PanelGrafica() {
        addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                mousePos = e.getPoint();
                detectHoveredPoint();
            }
        });
        addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseExited(java.awt.event.MouseEvent e) {
                hoveredPoint = null;
                repaint();
            }
        });
    }

    public void actualizarDatos(RepositorioCassandra.DatosGrafica datos) {
        this.datos = datos;
        this.hoveredPoint = null;
        repaint();
    }

    private void detectHoveredPoint() {
        if (datos == null || mousePos == null || datos.historial.isEmpty()) return;

        int ancho = getWidth();
        int alto = getHeight();
        int padding = 50;

        // Calcular los mismos límites que en paintComponent
        double minPrecio = Double.MAX_VALUE;
        double maxPrecio = Double.MIN_VALUE;
        long minTiempo = Long.MAX_VALUE;
        long maxTiempo = Long.MIN_VALUE;

        for (RepositorioCassandra.PuntoHistorico p : datos.historial) {
            if (p.precio < minPrecio) minPrecio = p.precio;
            if (p.precio > maxPrecio) maxPrecio = p.precio;
            long t = p.fecha.toEpochMilli();
            if (t < minTiempo) minTiempo = t;
            if (t > maxTiempo) maxTiempo = t;
        }

        for (RepositorioCassandra.PuntoHistorico tx : datos.transacciones) {
            if (tx.precio < minPrecio) minPrecio = tx.precio;
            if (tx.precio > maxPrecio) maxPrecio = tx.precio;
            long t = tx.fecha.toEpochMilli();
            if (t < minTiempo) minTiempo = t;
            if (t > maxTiempo) maxTiempo = t;
        }

        if (minPrecio == Double.MAX_VALUE) return;

        double rangoPrecio = maxPrecio - minPrecio;
        if (rangoPrecio == 0) rangoPrecio = 1.0;
        minPrecio -= rangoPrecio * 0.15;
        maxPrecio += rangoPrecio * 0.15;
        rangoPrecio = maxPrecio - minPrecio;

        long rangoTiempo = maxTiempo - minTiempo;
        if (rangoTiempo == 0) rangoTiempo = 1;

        RepositorioCassandra.PuntoHistorico bestHover = null;
        double bestDist = 10.0; // 10px radius

        // Buscar en transacciones (tienen prioridad para hover)
        for (RepositorioCassandra.PuntoHistorico tx : datos.transacciones) {
            long t = tx.fecha.toEpochMilli();
            double propX = (double) (t - minTiempo) / rangoTiempo;
            int x = padding + (int) (propX * (ancho - 2 * padding));
            int y = alto - padding - (int) (((tx.precio - minPrecio) / rangoPrecio) * (alto - 2 * padding));

            double dist = mousePos.distance(x, y);
            if (dist < bestDist) {
                bestDist = dist;
                bestHover = tx;
            }
        }

        // Si no hay transacción cerca, buscar en historial de precios
        if (bestHover == null) {
            for (RepositorioCassandra.PuntoHistorico p : datos.historial) {
                long t = p.fecha.toEpochMilli();
                double propX = (double) (t - minTiempo) / rangoTiempo;
                int x = padding + (int) (propX * (ancho - 2 * padding));
                int y = alto - padding - (int) (((p.precio - minPrecio) / rangoPrecio) * (alto - 2 * padding));

                double dist = mousePos.distance(x, y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestHover = p;
                }
            }
        }

        if (hoveredPoint != bestHover) {
            hoveredPoint = bestHover;
            repaint();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int ancho = getWidth();
        int alto = getHeight();
        int padding = 50;

        // Dibujar fondo oscuro
        g2.setColor(new Color(30, 30, 30));
        g2.fillRect(0, 0, ancho, alto);

        if (datos == null || datos.historial.isEmpty()) {
            g2.setColor(Color.WHITE);
            g2.drawString("Aún no hay datos para graficar. Lanza el simulador primero.", ancho / 2 - 150, alto / 2);
            return;
        }

        // Encontrar límites globales
        double minPrecio = Double.MAX_VALUE;
        double maxPrecio = Double.MIN_VALUE;
        long minTiempo = Long.MAX_VALUE;
        long maxTiempo = Long.MIN_VALUE;

        for (RepositorioCassandra.PuntoHistorico p : datos.historial) {
            if (p.precio < minPrecio) minPrecio = p.precio;
            if (p.precio > maxPrecio) maxPrecio = p.precio;
            long t = p.fecha.toEpochMilli();
            if (t < minTiempo) minTiempo = t;
            if (t > maxTiempo) maxTiempo = t;
        }

        for (RepositorioCassandra.PuntoHistorico tx : datos.transacciones) {
            if (tx.precio < minPrecio) minPrecio = tx.precio;
            if (tx.precio > maxPrecio) maxPrecio = tx.precio;
            long t = tx.fecha.toEpochMilli();
            if (t < minTiempo) minTiempo = t;
            if (t > maxTiempo) maxTiempo = t;
        }

        double rangoPrecio = maxPrecio - minPrecio;
        if (rangoPrecio == 0) rangoPrecio = 1.0;
        minPrecio -= rangoPrecio * 0.15;
        maxPrecio += rangoPrecio * 0.15;
        rangoPrecio = maxPrecio - minPrecio;

        long rangoTiempo = maxTiempo - minTiempo;
        if (rangoTiempo == 0) rangoTiempo = 1;

        // Dibujar ejes y marcas de escala
        g2.setColor(Color.GRAY);
        g2.drawLine(padding, alto - padding, ancho - padding, alto - padding); // X
        g2.drawLine(padding, padding, padding, alto - padding); // Y

        // Marcas y texto en el eje Y (precios)
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(Color.LIGHT_GRAY);
        int divisiones = 5;
        for (int i = 0; i <= divisiones; i++) {
            double precioVal = minPrecio + (rangoPrecio * i / divisiones);
            int yVal = alto - padding - (int) ((double) i / divisiones * (alto - 2 * padding));
            g2.drawLine(padding - 5, yVal, padding, yVal);
            g2.drawString(String.format(java.util.Locale.US, "€%.2f", precioVal), 8, yVal + 4);
        }

        // Dibujar línea del precio
        g2.setColor(new Color(0, 255, 127)); // Verde neón
        g2.setStroke(new BasicStroke(2));

        List<RepositorioCassandra.PuntoHistorico> puntos = datos.historial;
        int n = puntos.size();
        for (int i = 0; i < n - 1; i++) {
            long t1 = puntos.get(i).fecha.toEpochMilli();
            long t2 = puntos.get(i + 1).fecha.toEpochMilli();
            double propX1 = (double) (t1 - minTiempo) / rangoTiempo;
            double propX2 = (double) (t2 - minTiempo) / rangoTiempo;

            int x1 = padding + (int) (propX1 * (ancho - 2 * padding));
            int y1 = alto - padding - (int) (((puntos.get(i).precio - minPrecio) / rangoPrecio) * (alto - 2 * padding));
            int x2 = padding + (int) (propX2 * (ancho - 2 * padding));
            int y2 = alto - padding - (int) (((puntos.get(i + 1).precio - minPrecio) / rangoPrecio) * (alto - 2 * padding));
            g2.drawLine(x1, y1, x2, y2);
        }

        // Dibujar compras y ventas (Puntos rojos y blancos)
        int diametro = 10;
        int lastLabelX = -999;

        for (RepositorioCassandra.PuntoHistorico tx : datos.transacciones) {
            long t = tx.fecha.toEpochMilli();
            double proporcionX = (double) (t - minTiempo) / rangoTiempo;
            int x = padding + (int) (proporcionX * (ancho - 2 * padding));
            int y = alto - padding - (int) (((tx.precio - minPrecio) / rangoPrecio) * (alto - 2 * padding));
            
            if ("compra".equals(tx.tipo)) {
                g2.setColor(new Color(255, 50, 50)); // Rojo
            } else if ("venta".equals(tx.tipo)) {
                g2.setColor(Color.WHITE); // Blanco
            } else {
                continue;
            }
            g2.fillOval(x - diametro/2, y - diametro/2, diametro, diametro);

            // Dibujar precio exacto estático si no colisiona
            if (Math.abs(x - lastLabelX) > 40) {
                g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
                g2.drawString(String.format(java.util.Locale.US, "€%.2f", tx.precio), x - 15, "compra".equals(tx.tipo) ? y - 8 : y + 16);
                lastLabelX = x;
            }
        }
        
        // Dibujar tooltip de Hover
        if (hoveredPoint != null) {
            long t = hoveredPoint.fecha.toEpochMilli();
            double propX = (double) (t - minTiempo) / rangoTiempo;
            int x = padding + (int) (propX * (ancho - 2 * padding));
            int y = alto - padding - (int) (((hoveredPoint.precio - minPrecio) / rangoPrecio) * (alto - 2 * padding));

            // Resaltar punto hovered
            g2.setColor(new Color(0, 191, 255)); // Deep Sky Blue
            g2.setStroke(new BasicStroke(2));
            g2.drawOval(x - 8, y - 8, 16, 16);

            // Crear texto
            String fechaTexto = java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                .withZone(java.time.ZoneId.systemDefault())
                .format(hoveredPoint.fecha);
            String label = String.format(java.util.Locale.US, "Precio: €%.2f (%s)", hoveredPoint.precio, fechaTexto);
            if (hoveredPoint.tipo != null) {
                String tipoEtiqueta = hoveredPoint.tipo.equals("compra") ? "Compra" : "Venta a Tienda";
                label = String.format(java.util.Locale.US, "%s: €%.2f (%s)", tipoEtiqueta, hoveredPoint.precio, fechaTexto);
            }

            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            FontMetrics fm = g2.getFontMetrics();
            int tooltipAncho = fm.stringWidth(label) + 16;
            int tooltipAlto = 24;

            int tx = x - tooltipAncho / 2;
            int ty = y - 35;

            if (tx < 10) tx = 10;
            if (tx + tooltipAncho > ancho - 10) tx = ancho - tooltipAncho - 10;
            if (ty < 10) ty = y + 15;

            // Dibujar fondo del tooltip
            g2.setColor(new Color(20, 20, 20, 240));
            g2.fillRoundRect(tx, ty, tooltipAncho, tooltipAlto, 8, 8);
            
            // Dibujar borde
            g2.setColor(new Color(0, 191, 255));
            g2.drawRoundRect(tx, ty, tooltipAncho, tooltipAlto, 8, 8);

            // Dibujar texto
            g2.setColor(Color.WHITE);
            g2.drawString(label, tx + 8, ty + tooltipAlto - 8);
        }

        // Leyendas
        g2.setFont(new Font("SansSerif", Font.PLAIN, 11));
        
        g2.setColor(new Color(0, 255, 127));
        g2.fillRect(ancho - 170, 20, 15, 15);
        g2.setColor(Color.WHITE);
        g2.drawString("Evolución de Precio", ancho - 145, 32);

        g2.setColor(new Color(255, 50, 50));
        g2.fillOval(ancho - 170, 45, 15, 15);
        g2.setColor(Color.WHITE);
        g2.drawString("Compra", ancho - 145, 57);

        g2.setColor(Color.WHITE);
        g2.fillOval(ancho - 170, 70, 15, 15);
        g2.setColor(Color.WHITE);
        g2.drawString("Venta a Tienda", ancho - 145, 82);
    }
}
