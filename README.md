# MyBeaver: herramienta administrativa para MySQL

## Cómo ejecutarla

1. Define la variable de entorno `APP_ENCRYPTION_KEY` con una llave AES de 32 bytes en Base64
   (se usa para cifrar las contraseñas guardadas). Para generar una en PowerShell:
   `[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }))`
2. `./mvnw spring-boot:run` y abre <http://localhost:8080>.

Las conexiones guardadas quedan en `data/connections.json` (la contraseña va cifrada con AES-GCM).

## Cumplimiento de la rúbrica

| Requisito | Dónde / cómo |
|---|---|
| Inicio de sesión con cualquier usuario | Botón **Conectar** (sesión en memoria, sin guardar) |
| Varias conexiones guardadas | Botón **Guardar** → `data/connections.json` |
| Listar todos los tipos de objeto | Árbol del explorador (`GET .../objects/{tipo}`) |
| Creación visual (tablas y vistas) | Pestañas **Crear tabla** y **Crear vista** |
| DDL desde la metadata | Pestaña **Objeto** (`GET .../objects/{tipo}/ddl`) |
| Modificación mediante SQL / exportar DDL | Botones **Modificar en editor SQL** y **Exportar .sql** |
| Ejecutar SELECT y scripts DDL/DML | Pestaña **Editor SQL** (varias sentencias, `DELIMITER`, abrir/exportar `.sql`) |

## Fuente de la metadata por objeto

| Objeto | Listado | DDL |
|---|---|---|
| Bases de datos | `SHOW DATABASES` | – |
| Tablas | `SHOW FULL TABLES WHERE Table_type='BASE TABLE'` | `SHOW CREATE TABLE` |
| Columnas | `SHOW FULL COLUMNS FROM t` | – |
| Vistas | `SHOW FULL TABLES WHERE Table_type='VIEW'` | `SHOW CREATE VIEW` |
| Procedimientos | `SHOW PROCEDURE STATUS WHERE Db = ?` | `SHOW CREATE PROCEDURE` |
| Funciones | `SHOW FUNCTION STATUS WHERE Db = ?` | `SHOW CREATE FUNCTION` |
| Triggers | `SHOW TRIGGERS` | `SHOW CREATE TRIGGER` |
| Eventos | `SHOW EVENTS` | `SHOW CREATE EVENT` |
| Índices | `SHOW INDEX FROM t` (por cada tabla) | Se arma `CREATE INDEX` / `ADD PRIMARY KEY` con los datos de `SHOW INDEX` |
| Secuencias | `SHOW CREATE TABLE` (columna `AUTO_INCREMENT`) | Se arma `ALTER TABLE ... AUTO_INCREMENT = n` |
| Tablespaces | `performance_schema.file_instances` + `SHOW VARIABLES` | Se arma `CREATE TABLESPACE` (generales) |
| Usuarios | `SELECT User, Host FROM mysql.user` | `SHOW CREATE USER` + `SHOW GRANTS FOR` |

## Limitaciones y diferencias de MySQL (justificación)

- **Paquetes: no aplica.** MySQL no tiene `CREATE PACKAGE` (es de Oracle PL/SQL). La forma de agrupar
  lógica es colocar los procedimientos y funciones relacionados en la misma base de datos. En el árbol
  aparece como «no aplica» con esta explicación.
- **Secuencias: no existen como objeto.** MySQL (a diferencia de MariaDB, Oracle o PostgreSQL) no tiene
  `CREATE SEQUENCE`. Su generador equivalente es la columna `AUTO_INCREMENT`; la herramienta lista esas
  columnas con su siguiente valor (leído de `SHOW CREATE TABLE`, que es exacto; `SHOW TABLE STATUS` puede
  venir de una caché de estadísticas).
- **Tablespaces:** MySQL 8 guarda su catálogo en el diccionario de datos interno, al que solo se llega
  por `information_schema` (prohibido). Por eso se usan los archivos de datos de InnoDB que reporta la
  tabla de sistema `performance_schema.file_instances`, y el tipo de tablespace se deduce de la ruta.
  Solo los tablespaces *generales* tienen un `CREATE TABLESPACE` reconstruible; los de sistema, undo y
  temporales se definen con variables del servidor (se muestran como comentario) y los «archivo por tabla»
  se crean junto con la tabla. Si el usuario no puede leer `performance_schema`, solo se muestran
  `innodb_system` e `innodb_temporary` (deducidos de `SHOW VARIABLES`).
- **Usuarios y tablespaces son objetos del servidor**, no de una base de datos: se listan igual
  sea cual sea la base elegida. Ver usuarios requiere permiso de lectura sobre `mysql.user`; si no lo hay,
  la aplicación muestra un error 403 claro.
- **Cuerpo de rutinas:** `SHOW CREATE PROCEDURE/FUNCTION` devuelve el código solo al DEFINER o a quien
  tenga `SHOW_ROUTINE`; si no, se informa la falta de privilegio.
- **Modificar** procedimientos, funciones, triggers, eventos e índices se hace con `DROP` + `CREATE`
  (MySQL no permite cambiar su cuerpo con `ALTER`). Las vistas usan `CREATE OR REPLACE VIEW` y las tablas
  `ALTER TABLE`.
- **Editor SQL:** si una sentencia falla, el script se detiene y las anteriores quedan aplicadas
  (MySQL hace *commit* implícito en DDL). Se muestran como máximo 5000 filas por resultado.
  Un `CREATE PROCEDURE ... BEGIN ... END` escrito sin `DELIMITER` se envía completo al servidor para que
  lo separe él (opción `allowMultiQueries` del driver).
- El campo `DEFAULT` del diseñador de tablas trata como texto todo lo que no sea número, `NULL` o
  `CURRENT_TIMESTAMP`.
