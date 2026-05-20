CREATE KEYSPACE IF NOT EXISTS mtg_catalog WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 2};

USE mtg_catalog;

CREATE TABLE IF NOT EXISTS cartas (
    id uuid PRIMARY KEY,
    name text,
    set_name text,
    mana_cost text,
    type_line text,
    oracle_text text,
    colors text,
    usd_price text,
    eur_price text
);

CREATE TABLE IF NOT EXISTS cartas_por_edicion (
    set_name text,
    name text,
    id uuid,
    usd_price text,
    eur_price text,
    PRIMARY KEY ((set_name), name, id)
);

CREATE TABLE IF NOT EXISTS inventario_por_caja (
    nombre_caja text,
    carta_id uuid,
    nombre_carta text,
    edicion text,
    precio_eur text,
    cantidad int,
    PRIMARY KEY ((nombre_caja), carta_id)
);

CREATE TABLE IF NOT EXISTS historial_precios (
    carta_id uuid,
    fecha timestamp,
    precio_eur double,
    PRIMARY KEY (carta_id, fecha)
) WITH CLUSTERING ORDER BY (fecha DESC);

CREATE TABLE IF NOT EXISTS transacciones (
    carta_id uuid,
    fecha timestamp,
    tipo text, -- "compra" o "venta"
    precio_eur double,
    PRIMARY KEY (carta_id, fecha)
) WITH CLUSTERING ORDER BY (fecha DESC);

CREATE TABLE IF NOT EXISTS balance_tienda (
    tienda_id text,
    fecha timestamp,
    balance double,
    tipo_operacion text, -- "inicial", "compra_cliente", "venta_cliente", "reabastecimiento"
    monto double,
    PRIMARY KEY (tienda_id, fecha)
) WITH CLUSTERING ORDER BY (fecha DESC);


