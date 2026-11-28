import { request, post, el, enc, buildGrid, ident, literal, downloadText, safeFileName, showStatus, withBusy } from "./api.js";
import { state, emit, on, dbPath } from "./state.js";

const $ = (id) => document.getElementById(id);

const SYSTEM_DBS = ["information_schema", "mysql", "performance_schema", "sys"];

let current = null;
let openToken = 0;

export function setStatus(message, kind) {
    showStatus($("ws-status"), message, kind);
}

export function clearStatus() {
    $("ws-status").hidden = true;
}

export function selectTab(name) {
    document.querySelectorAll(".tab").forEach((tab) => {
        tab.setAttribute("aria-selected", String(tab.dataset.tab === name));
    });
    document.querySelectorAll(".tab-panel").forEach((panel) => {
        panel.hidden = panel.id !== "tab-" + name;
    });
}

export function requireDatabase() {
    if (state.database) return true;
    setStatus("Primero elige una base de datos en el panel izquierdo.", "error");
    return false;
}

export async function openWorkspace(conn) {
    if (state.types.length === 0) state.types = await request("/object-types");
    state.conn = { id: conn.id, name: conn.name, temporary: conn.temporary };
    $("ws-conn-name").textContent = conn.name + (conn.temporary ? " · sesión sin guardar" : "");
    fillDatabases(conn.databases || [], null);
    clearStatus();
    selectTab("detail");
    showEmptyDetail();
}

function fillDatabases(names, keep) {
    const select = $("ws-db");
    const options = names.map((n) => el("option", { value: n }, n));
    select.replaceChildren(el("option", { value: "" }, "— elige una base —"), ...options);

    const preferred = (keep && names.includes(keep)) ? keep
        : names.find((n) => !SYSTEM_DBS.includes(String(n).toLowerCase())) || "";
    select.value = preferred;
    changeDatabase(preferred || null);
}

function changeDatabase(name) {
    state.database = name;
    buildTree();
    showEmptyDetail();
    emit("database-changed", name);
}

async function reloadDatabases() {
    const names = await request("/connections/" + enc(state.conn.id) + "/databases");
    fillDatabases(names, state.database);
}

function buildTree() {
    const tree = $("tree");
    if (!state.database) {
        tree.replaceChildren(el("p", { className: "hint small" }, "Elige una base de datos para ver sus objetos."));
        return;
    }
    tree.replaceChildren(...state.types.map((type) => {
        const count = el("span", { className: "tree-count" });
        const label = type.label + (type.scope === "server" ? " (servidor)" : "");
        const summary = el("summary", {}, el("span", {}, label), count);
        const body = el("div", { className: "tree-body" });
        const details = el("details", { className: "tree-group", dataset: { type: type.urlName } }, summary, body);

        if (!type.supported) {
            details.classList.add("unsupported");
            count.textContent = "no aplica";
            body.append(el("p", { className: "hint small" }, type.note));
            return details;
        }
        details.addEventListener("toggle", () => {
            if (details.open && !details.dataset.loaded) loadGroup(details, type);
        });
        return details;
    }));
}

async function loadGroup(details, type) {
    const body = details.querySelector(".tree-body");
    const count = details.querySelector(".tree-count");
    const database = state.database;
    body.replaceChildren(el("p", { className: "hint small" }, "Cargando…"));
    details.dataset.loaded = "1";
    try {
        const items = await request(dbPath() + "/objects/" + type.urlName);
        if (database !== state.database) return;
        count.textContent = String(items.length);

        const children = [];
        if (type.note) children.push(el("p", { className: "hint small" }, type.note));
        if (items.length === 0) {
            children.push(el("p", { className: "hint small" }, "(vacío)"));
        } else {
            children.push(el("ul", { className: "tree-items" }, ...items.map((obj) => {
                const button = el("button", { type: "button", className: "tree-item", title: obj.detail || obj.name },
                    el("span", { className: "tree-name" }, obj.name),
                    obj.detail ? el("span", { className: "tree-detail" }, obj.detail) : null);
                button.addEventListener("click", () => openObject(type, obj));
                return el("li", {}, button);
            })));
        }
        body.replaceChildren(...children);
    } catch (err) {
        delete details.dataset.loaded;
        count.textContent = "!";
        body.replaceChildren(el("p", { className: "hint small error-text" }, err.message));
    }
}

function reloadTree() {
    document.querySelectorAll(".tree-group").forEach((details) => {
        if (details.classList.contains("unsupported")) return;
        delete details.dataset.loaded;
        details.querySelector(".tree-count").textContent = "";
        if (details.open) {
            const type = state.types.find((t) => t.urlName === details.dataset.type);
            loadGroup(details, type);
        }
    });
}

function showEmptyDetail() {
    current = null;
    $("detail").hidden = true;
    $("detail-empty").hidden = false;
}

async function openObject(type, obj) {
    const token = ++openToken;
    current = { type, obj, ddl: "" };
    selectTab("detail");
    clearStatus();

    const isTable = type.urlName === "tables";
    $("detail-empty").hidden = true;
    $("detail").hidden = false;
    $("detail-type").textContent = type.label;
    $("detail-name").textContent = obj.name;
    $("detail-sub").textContent = obj.detail || "";
    $("detail-ddl").value = "Cargando…";
    $("detail-table-extra").hidden = !isTable;
    $("obj-data").hidden = !(isTable || type.urlName === "views");
    $("obj-drop").hidden = dropSql(type, obj) === null;

    const params = "?name=" + enc(obj.name) + (obj.parent ? "&table=" + enc(obj.parent) : "");
    const ddlPromise = request(dbPath() + "/objects/" + type.urlName + "/ddl" + params);

    if (isTable) {
        $("detail-columns").replaceChildren(el("p", { className: "hint small" }, "Cargando…"));
        $("detail-indexes").replaceChildren();
        const q = "?table=" + enc(obj.name);
        Promise.all([request(dbPath() + "/columns" + q), request(dbPath() + "/indexes" + q)])
            .then(([columns, indexes]) => {
                if (token !== openToken) return;
                $("detail-columns").replaceChildren(buildGrid(
                    ["Columna", "Tipo", "NULL", "Llave", "Default", "Extra", "Comentario"],
                    columns.map((c) => [c.name, c.type, c.nullable ? "Sí" : "No", c.key, c.default, c.extra, c.comment])));
                $("detail-indexes").replaceChildren(indexes.length === 0
                    ? el("p", { className: "hint small" }, "La tabla no tiene índices.")
                    : buildGrid(["Índice", "Único", "Columnas", "Tipo"],
                        indexes.map((i) => [i.name, i.unique ? "Sí" : "No", i.columns.join(", "), i.type])));
            })
            .catch((err) => {
                if (token === openToken) $("detail-columns").replaceChildren(el("p", { className: "error-text" }, err.message));
            });
    }

    try {
        const ddl = await ddlPromise;
        if (token !== openToken) return;
        current.ddl = ddl;
        $("detail-ddl").value = ddl;
    } catch (err) {
        if (token !== openToken) return;
        $("detail-ddl").value = "-- No se pudo generar el DDL:\n-- " + err.message;
    }
}

function account(name) {
    const at = name.lastIndexOf("@");
    return literal(name.slice(0, at)) + "@" + literal(name.slice(at + 1));
}

function dropSql(type, obj) {
    const n = ident(obj.name);
    switch (type.urlName) {
        case "tables": return "DROP TABLE " + n + ";";
        case "views": return "DROP VIEW " + n + ";";
        case "procedures": return "DROP PROCEDURE " + n + ";";
        case "functions": return "DROP FUNCTION " + n + ";";
        case "triggers": return "DROP TRIGGER " + n + ";";
        case "events": return "DROP EVENT " + n + ";";
        case "indexes": return obj.name === "PRIMARY"
            ? "ALTER TABLE " + ident(obj.parent) + " DROP PRIMARY KEY;"
            : "DROP INDEX " + n + " ON " + ident(obj.parent) + ";";
        case "users": return "DROP USER " + account(obj.name) + ";";
        default: return null;
    }
}

function modificationScript(type, obj, ddl) {
    const n = ident(obj.name);
    switch (type.urlName) {
        case "tables":
            return "-- DDL actual (referencia):\n" + ddl.split("\n").map((l) => "-- " + l).join("\n") +
                "\n\n-- Escribe aquí los cambios. Ejemplos:\n" +
                "ALTER TABLE " + n + "\n  ADD COLUMN nueva_columna VARCHAR(50) NULL;\n" +
                "-- ALTER TABLE " + n + " MODIFY COLUMN columna INT NOT NULL;\n" +
                "-- ALTER TABLE " + n + " DROP COLUMN columna;\n" +
                "-- ALTER TABLE " + n + " RENAME TO nuevo_nombre;\n";
        case "views":
            return ddl.replace(/^CREATE\s+/i, "CREATE OR REPLACE ");
        case "procedures":
        case "functions":
        case "triggers":
        case "events": {
            const keyword = { procedures: "PROCEDURE", functions: "FUNCTION", triggers: "TRIGGER", events: "EVENT" }[type.urlName];
            return "-- Para modificarlo se elimina y se vuelve a crear con el cuerpo editado\n" +
                "DROP " + keyword + " IF EXISTS " + n + ";\n" + ddl;
        }
        case "indexes":
            return "-- Para modificar un índice se elimina y se vuelve a crear\n" + dropSql(type, obj) + "\n" + ddl;
        case "users":
            return "-- Ejemplos de modificación de un usuario:\n" +
                "-- ALTER USER " + account(obj.name) + " IDENTIFIED BY 'nueva_clave';\n" +
                "-- GRANT SELECT ON base.* TO " + account(obj.name) + ";\n" +
                "-- REVOKE SELECT ON base.* FROM " + account(obj.name) + ";\n\n" + ddl;
        default:
            return ddl;
    }
}

export function initWorkspace(onClose) {
    document.querySelector(".tabs").addEventListener("click", (event) => {
        const tab = event.target.closest(".tab");
        if (tab) selectTab(tab.dataset.tab);
    });

    $("ws-db").addEventListener("change", (event) => changeDatabase(event.target.value || null));

    $("ws-refresh").addEventListener("click", (event) => withBusy(event.target, async () => {
        try {
            await reloadDatabases();
            setStatus("Lista de bases y objetos actualizada.", "ok");
        } catch (err) {
            setStatus(err.message, "error");
        }
    }));

    $("ws-new-db").addEventListener("click", (event) => {
        const name = window.prompt("Nombre de la nueva base de datos:");
        if (!name || !name.trim()) return;
        withBusy(event.target, async () => {
            try {
                const result = await post("/connections/" + enc(state.conn.id) + "/sql",
                    { sql: "CREATE DATABASE " + ident(name.trim()) });
                if (result.failed) throw new Error(result.results.at(-1).message);
                const names = await request("/connections/" + enc(state.conn.id) + "/databases");
                fillDatabases(names, name.trim());
                setStatus("Base de datos \"" + name.trim() + "\" creada.", "ok");
            } catch (err) {
                setStatus(err.message, "error");
            }
        });
    });

    $("ws-disconnect").addEventListener("click", async () => {
        if (state.conn?.temporary) {
            await request("/connections/session/" + enc(state.conn.id), { method: "DELETE" }).catch(() => {});
        }
        state.conn = null;
        state.database = null;
        onClose();
    });

    $("ddl-copy").addEventListener("click", async () => {
        try {
            await navigator.clipboard.writeText($("detail-ddl").value);
            setStatus("DDL copiado al portapapeles.", "ok");
        } catch {
            $("detail-ddl").select();
            setStatus("Selecciona el texto y cópialo con Ctrl+C.", "");
        }
    });

    $("ddl-export").addEventListener("click", () => {
        if (!current) return;
        downloadText(safeFileName(current.type.urlName + "_" + current.obj.name) + ".sql", $("detail-ddl").value + "\n");
    });

    $("ddl-edit").addEventListener("click", () => {
        if (!current) return;
        emit("open-sql", { sql: modificationScript(current.type, current.obj, $("detail-ddl").value), run: false });
        setStatus("Edita el script y pulsa «Ejecutar» para aplicar los cambios.", "");
    });

    $("obj-data").addEventListener("click", () => {
        if (!current) return;
        emit("open-sql", { sql: "SELECT * FROM " + ident(current.obj.name) + " LIMIT 100;", run: true });
    });

    $("obj-drop").addEventListener("click", () => {
        if (!current) return;
        emit("open-sql", {
            sql: "-- ¡Cuidado! Esto elimina el objeto. Revisa y pulsa «Ejecutar» si estás seguro.\n" + dropSql(current.type, current.obj),
            run: false
        });
    });

    on("objects-changed", () => reloadTree());

    on("open-object", ({ typeUrl, name }) => {
        const type = state.types.find((t) => t.urlName === typeUrl);
        if (type) openObject(type, { name });
    });
}
