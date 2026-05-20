package com.bdnr.scryfall;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.fasterxml.jackson.databind.ObjectMapper;

public class TiendaGUI extends JFrame {

    private RepositorioCassandra db;
    private HttpClient client;
    private ObjectMapper mapper;

    // Elementos de la interfaz
    private JTextArea consolaVisor;
    private JComboBox<String> comboVersiones;
    
    // NUEVO: Variables para las cajas
    private JComboBox<String> comboCajasDestino;
    private JComboBox<String> comboCajasConsulta;
    private int contadorCajas = 1;
    
    private List<CartaMtg> listaCartasTemporal;

    // Variables para la gráfica
    private PanelGrafica panelGrafica;
    private JComboBox<String> comboCartasGrafica;
    private java.util.Map<String, java.util.UUID> mapaCartasGrafica = new java.util.LinkedHashMap<>();

    public TiendaGUI() {
        // Inicializar herramientas
        db = new RepositorioCassandra();
        client = HttpClient.newHttpClient();
        mapper = new ObjectMapper();

        // Configurar ventana principal
        setTitle("MTG Store Simulator - Cassandra Backend");
        setSize(800, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Inicializamos los desplegables vacíos
        comboCajasDestino = new JComboBox<>();
        comboCajasConsulta = new JComboBox<>();

        // Crear el panel de pestañas
        JTabbedPane pestañas = new JTabbedPane();
        pestañas.addTab("🛒 Comprar/Ingresar Stock", crearPanelCompra());
        pestañas.addTab("📦 Consultar Caja", crearPanelCajas());
        pestañas.addTab("⚡ Simulador / Stress Test", crearPanelSimulador());
        pestañas.addTab("📊 Gráfica de Mercado", crearPanelGrafica());

        // Consola visual en la parte inferior
        consolaVisor = new JTextArea(10, 50);
        consolaVisor.setEditable(false);
        consolaVisor.setBackground(Color.BLACK);
        consolaVisor.setForeground(Color.GREEN);
        JScrollPane scrollConsola = new JScrollPane(consolaVisor);
        
        add(pestañas, BorderLayout.CENTER);
        add(scrollConsola, BorderLayout.SOUTH);

        log("✅ Interfaz gráfica iniciada. Conectado a Cassandra.");
        
        // NUEVO: Cargamos las cajas de la BD al abrir el programa
        cargarCajasExistentes();
    }

    // --- NUEVO MÉTODO: GESTIÓN DE CAJAS ---
    private void cargarCajasExistentes() {
        List<String> cajasBD = db.obtenerNombresCajas();
        int maxNum = 0;

        for (String caja : cajasBD) {
            comboCajasDestino.addItem(caja);
            comboCajasConsulta.addItem(caja);
            
            // Calculamos cuál es el número más alto de caja creado
            if (caja.startsWith("Caja ")) {
                try {
                    int num = Integer.parseInt(caja.replace("Caja ", ""));
                    if (num > maxNum) maxNum = num;
                } catch (Exception e) { /* Ignorar si alguien llamó a la caja "Caja Fuerte" */ }
            }
        }
        
        // El contador será el máximo existente + 1
        contadorCajas = maxNum + 1;

        // Si la base de datos está completamente vacía, creamos la primera
        if (comboCajasDestino.getItemCount() == 0) {
            crearNuevaCaja();
        }
    }

    private void crearNuevaCaja() {
        String nuevaCaja = "Caja " + contadorCajas;
        comboCajasDestino.addItem(nuevaCaja);
        comboCajasConsulta.addItem(nuevaCaja);
        comboCajasDestino.setSelectedItem(nuevaCaja);
        contadorCajas++;
        log("🆕 Etiqueta creada: '" + nuevaCaja + "'. (Se registrará en Cassandra al guardar stock).");
    }

    // --- PANEL 1: BÚSQUEDA E INGRESO DE STOCK ---
    private JPanel crearPanelCompra() {
        JPanel panel = new JPanel(new GridLayout(6, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextField txtBusqueda = new JTextField();
        JButton btnBuscar = new JButton("Buscar en Scryfall");
        
        comboVersiones = new JComboBox<>();
        JTextField txtCantidad = new JTextField("1");
        
        // NUEVO: Agrupamos el desplegable y el botón en un solo sub-panel
        JPanel panelSelectorCaja = new JPanel(new BorderLayout(5, 0));
        JButton btnCrearCaja = new JButton("+ Crear Caja");
        panelSelectorCaja.add(comboCajasDestino, BorderLayout.CENTER);
        panelSelectorCaja.add(btnCrearCaja, BorderLayout.EAST);
        
        JButton btnGuardar = new JButton("Guardar en Inventario");

        // Acción: Buscar carta
        btnBuscar.addActionListener(e -> buscarCartaAsincrono(txtBusqueda.getText()));

        // Acción: Crear nueva caja
        btnCrearCaja.addActionListener(e -> crearNuevaCaja());

        // Acción: Guardar carta en base de datos
        btnGuardar.addActionListener(e -> {
            int index = comboVersiones.getSelectedIndex();
            if (index >= 0 && listaCartasTemporal != null && comboCajasDestino.getSelectedItem() != null) {
                CartaMtg cartaSeleccionada = listaCartasTemporal.get(index);
                try {
                    int cantidad = Integer.parseInt(txtCantidad.getText());
                    String cajaElegida = comboCajasDestino.getSelectedItem().toString();
                    
                    db.guardarCarta(cartaSeleccionada, true);
                    db.ingresarStock(cartaSeleccionada, cajaElegida, cantidad);
                    
                    log("📥 " + cantidad + "x " + cartaSeleccionada.getName() + " añadidos a [" + cajaElegida + "].");
                } catch (NumberFormatException ex) {
                    log("❌ Error: La cantidad debe ser un número.");
                }
            } else {
                log("⚠️ Busca y selecciona una carta primero.");
            }
        });

        panel.add(new JLabel("Nombre de la Carta:"));
        panel.add(txtBusqueda);
        panel.add(new JLabel("")); 
        panel.add(btnBuscar);
        panel.add(new JLabel("Elige Versión/Edición:"));
        panel.add(comboVersiones);
        panel.add(new JLabel("Cantidad comprada:"));
        panel.add(txtCantidad);
        panel.add(new JLabel("Selecciona Destino:"));
        panel.add(panelSelectorCaja); // Añadimos nuestro panel compuesto
        panel.add(new JLabel(""));
        panel.add(btnGuardar);

        return panel;
    }

    // --- PANEL 2: CONSULTA DE CAJAS ---
    private JPanel crearPanelCajas() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel panelSuperior = new JPanel(new FlowLayout());
        JButton btnRevisar = new JButton("Hacer Arqueo de Caja");

        panelSuperior.add(new JLabel("Elige una Caja:"));
        panelSuperior.add(comboCajasConsulta); // Usamos el segundo desplegable sincronizado
        panelSuperior.add(btnRevisar);

        JTextArea visorResultados = new JTextArea();
        visorResultados.setEditable(false);
        visorResultados.setFont(new Font("Monospaced", Font.PLAIN, 14));
        
        btnRevisar.addActionListener(e -> {
            if (comboCajasConsulta.getSelectedItem() != null) {
                String cajaElegida = comboCajasConsulta.getSelectedItem().toString();
                log("📦 Solicitando arqueo de: " + cajaElegida);
                
                String reporteCompletado = db.obtenerEstadisticasCaja(cajaElegida);
                visorResultados.setText(reporteCompletado);
                visorResultados.setCaretPosition(0); 
            }
        });

        panel.add(panelSuperior, BorderLayout.NORTH);
        panel.add(new JScrollPane(visorResultados), BorderLayout.CENTER);

        return panel;
    }

    // --- PANEL 3: SIMULADOR DE CARGA ---
    private JPanel crearPanelSimulador() {
        JPanel panel = new JPanel(new GridLayout(4, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JTextField txtClientes = new JTextField("50");
        JTextField txtPeticiones = new JTextField("100");
        JButton btnIniciar = new JButton("Lanzar Simulador de Clientes");

        btnIniciar.addActionListener(e -> {
            try {
                int clientes = Integer.parseInt(txtClientes.getText());
                int peticiones = Integer.parseInt(txtPeticiones.getText());
                log("🚀 Lanzando simulador con " + clientes + " clientes haciendo " + peticiones + " peticiones c/u...");
                
                // Ejecutamos en un hilo separado para no bloquear la UI
                new Thread(() -> {
                    SimuladorClientes simulador = new SimuladorClientes(db, clientes, peticiones);
                    simulador.iniciarSimulacion();
                    log("✅ Simulación completada. Revisa la terminal del sistema para ver las métricas (Throughput).");
                }).start();
                
            } catch (NumberFormatException ex) {
                log("❌ Error: Los valores deben ser números enteros.");
            }
        });

        panel.add(new JLabel("Número de Clientes (Hilos):"));
        panel.add(txtClientes);
        panel.add(new JLabel("Peticiones por Cliente:"));
        panel.add(txtPeticiones);
        panel.add(new JLabel(""));
        panel.add(btnIniciar);

        return panel;
    }

    // --- PANEL 4: GRÁFICA DE MERCADO ---
    private JPanel crearPanelGrafica() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Panel superior: selector de carta + botón actualizar
        JPanel panelSuperior = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 10, 5));
        comboCartasGrafica = new JComboBox<>();
        comboCartasGrafica.setPreferredSize(new java.awt.Dimension(350, 28));
        JButton btnActualizar = new JButton("Actualizar Gráfica");
        JButton btnCargarCartas = new JButton("🔄 Cargar Cartas");

        panelSuperior.add(new JLabel("Carta:"));
        panelSuperior.add(comboCartasGrafica);
        panelSuperior.add(btnCargarCartas);
        panelSuperior.add(btnActualizar);

        // Panel central: el lienzo de dibujo
        panelGrafica = new PanelGrafica();
        panelGrafica.setPreferredSize(new java.awt.Dimension(700, 400));

        // Panel inferior: leyenda de estadísticas
        JLabel lblStats = new JLabel("  Selecciona una carta y pulsa 'Actualizar Gráfica' para ver su historial de precios.");
        lblStats.setFont(new Font("SansSerif", Font.ITALIC, 12));

        // Acción: Cargar lista de cartas disponibles de la BD
        btnCargarCartas.addActionListener(e -> new Thread(() -> {
            java.util.Map<java.util.UUID, Double> cartas = db.obtenerTodasLasCartasConPrecio();
            // Para obtener los nombres, consultamos la tabla cartas
            java.util.Map<java.util.UUID, String> nombres = db.obtenerNombresCartas();
            SwingUtilities.invokeLater(() -> {
                comboCartasGrafica.removeAllItems();
                mapaCartasGrafica.clear();
                for (java.util.UUID id : cartas.keySet()) {
                    String nombre = nombres.getOrDefault(id, id.toString());
                    String display = nombre + String.format(" (€%.2f)", cartas.get(id));
                    comboCartasGrafica.addItem(display);
                    mapaCartasGrafica.put(display, id);
                }
                log("📊 " + cartas.size() + " cartas cargadas en el selector de gráfica.");
            });
        }).start());

        // Acción: Actualizar la gráfica con los datos de Cassandra
        btnActualizar.addActionListener(e -> {
            String seleccion = (String) comboCartasGrafica.getSelectedItem();
            if (seleccion == null || !mapaCartasGrafica.containsKey(seleccion)) {
                log("⚠️ Selecciona una carta del desplegable (pulsa 'Cargar Cartas' primero).");
                return;
            }
            java.util.UUID cartaId = mapaCartasGrafica.get(seleccion);
            log("📊 Cargando datos de la gráfica para: " + seleccion);

            new Thread(() -> {
                RepositorioCassandra.DatosGrafica datos = db.obtenerDatosGrafica(cartaId);
                int compras = datos.comprasFomo.size();
                int precios = datos.historial.size();
                SwingUtilities.invokeLater(() -> {
                    panelGrafica.actualizarDatos(datos);
                    lblStats.setText(String.format(
                        "  📈 %d cambios de precio registrados  |  🔴 %d compras por FOMO detectadas",
                        precios, compras
                    ));
                });
            }).start();
        });

        panel.add(panelSuperior, BorderLayout.NORTH);
        panel.add(panelGrafica, BorderLayout.CENTER);
        panel.add(lblStats, BorderLayout.SOUTH);
        return panel;
    }

    // --- LÓGICA DE RED (Asíncrona) ---
    private void buscarCartaAsincrono(String nombreCarta) {
        log("🔍 Buscando '" + nombreCarta + "'...");
        comboVersiones.removeAllItems();

        new Thread(() -> {
            try {
                String queryBusqueda = "!\"" + nombreCarta + "\"";
                String busquedaCodificada = URLEncoder.encode(queryBusqueda, StandardCharsets.UTF_8.toString());

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.scryfall.com/cards/search?q=" + busquedaCodificada + "&unique=prints"))
                        .header("User-Agent", "CatalogoCartasMtg/1.0")
                        .header("Accept", "*/*")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    RespuestaScryfall respuesta = mapper.readValue(response.body(), RespuestaScryfall.class);
                    listaCartasTemporal = respuesta.getData();

                    SwingUtilities.invokeLater(() -> {
                        for (CartaMtg carta : listaCartasTemporal) {
                            String item = String.format("[%s] %s - €%s", 
                                carta.getSet_name().toUpperCase(), carta.getName(), carta.getEur_price());
                            comboVersiones.addItem(item);
                        }
                        log("✨ Encontradas " + listaCartasTemporal.size() + " versiones.");
                    });
                } else {
                    log("❌ Scryfall no encontró la carta exacta. Código: " + response.statusCode());
                }
            } catch (Exception ex) {
                log("💥 Error de conexión: " + ex.getMessage());
            }
        }).start();
    }

    // Método para escribir en nuestra "consola" negra de la interfaz
    private void log(String mensaje) {
        SwingUtilities.invokeLater(() -> {
            consolaVisor.append(mensaje + "\n");
            consolaVisor.setCaretPosition(consolaVisor.getDocument().getLength());
        });
    }

    // --- ARRANQUE DE LA APP ---
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            TiendaGUI app = new TiendaGUI();
            app.setVisible(true);
        });
    }
}