

TABLA INVENTARIO POR CAJA:
USE mtg_catalog;

CREATE TABLE inventario_por_caja (
    nombre_caja text,
    carta_id uuid,
    nombre_carta text,
    edicion text,
    precio_eur text,
    cantidad int,
    PRIMARY KEY ((nombre_caja), carta_id)
);