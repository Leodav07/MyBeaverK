import { post, el, withBusy } from "./api.js";
import { state, emit, dbPath } from "./state.js";
import { setStatus, clearStatus, requireDatabase } from "./workspace.js";

const $ = (id) => document.getElementById(id);

const TYPES = [
    "INT", "BIGINT", "SMALLINT", "TINYINT", "MEDIUMINT", "DECIMAL", "FLOAT", "DOUBLE", "BOOLEAN",
    "VARCHAR", "CHAR", "TEXT", "TINYTEXT", "MEDIUMTEXT", "LONGTEXT",
    "DATE", "DATETIME", "TIMESTAMP", "TIME", "YEAR",
    "JSON", "ENUM", "SET", "BLOB", "MEDIUMBLOB", "LONGBLOB", "BINARY", "VARBINARY"
];

export function initCreateForms() {
    initTableForm();
    initViewForm();
}

function initTableForm() {
    const body = $("ct-columns");

    function addColumn(def = {}) {
        const check = (checked, title) => el("input", { type: "checkbox", checked: !!checked, title });
        const type = el("select", { title: "Tipo" }, ...TYPES.map((t) => el("option", { value: t }, t)));
        type.value = def.type || "VARCHAR";

        const cells = {
            name: el("input", { type: "text", value: def.name || "", placeholder: "columna", title: "Nombre" }),
            type,
            length: el("input", { type: "text", value: def.length ?? (type.value === "VARCHAR" ? "100" : ""), className: "short", title: "Longitud" }),
            pk: check(def.pk, "Llave primaria"),
            nullable: check(def.nullable ?? true, "Permite NULL"),
            ai: check(def.ai, "AUTO_INCREMENT"),
            uq: check(def.uq, "Único"),
            def: el("input", { type: "text", value: def.defaultValue || "", className: "short", title: "Valor por defecto" }),
            comment: el("input", { type: "text", value: "", title: "Comentario" })
        };

        const syncPk = () => {
            if (cells.pk.checked) cells.nullable.checked = false;
            cells.nullable.disabled = cells.pk.checked;
        };
        cells.pk.addEventListener("change", syncPk);
        syncPk();

        type.addEventListener("change", () => {
            if (type.value === "VARCHAR" && !cells.length.value) cells.length.value = "100";
            if (!["VARCHAR", "CHAR", "DECIMAL", "ENUM", "SET", "BINARY", "VARBINARY", "DATETIME", "TIMESTAMP", "TIME"].includes(type.value)) {
                cells.length.value = "";
            }
        });

        const remove = el("button", { type: "button", className: "danger small", title: "Quitar columna" }, "✕");
        const tr = el("tr", {}, ...Object.values(cells).map((c) => el("td", {}, c)), el("td", {}, remove));
        tr.cells_ = cells;
        remove.addEventListener("click", () => tr.remove());
        body.append(tr);
        return cells;
    }

    function readRequest(execute) {
        return {
            name: $("ct-name").value.trim(),
            engine: $("ct-engine").value,
            comment: $("ct-comment").value.trim() || null,
            execute,
            columns: [...body.rows].map((tr) => {
                const c = tr.cells_;
                return {
                    name: c.name.value.trim(),
                    type: c.type.value,
                    length: c.length.value.trim() || null,
                    primaryKey: c.pk.checked,
                    nullable: c.nullable.checked,
                    autoIncrement: c.ai.checked,
                    unique: c.uq.checked,
                    defaultValue: c.def.value.trim() || null,
                    comment: c.comment.value.trim() || null
                };
            })
        };
    }

    function reset() {
        $("ct-form").reset();
        body.replaceChildren();
        addColumn({ name: "id", type: "INT", length: "", pk: true, ai: true });
        addColumn({ name: "nombre", type: "VARCHAR", length: "100" });
    }

    async function send(execute, button) {
        if (!requireDatabase()) return;
        clearStatus();
        await withBusy(button, async () => {
            try {
                const request = readRequest(execute);
                const result = await post(dbPath() + "/create/table", request);
                $("ct-sql").textContent = result.sql;
                $("ct-sql").hidden = false;
                if (result.executed) {
                    reset();
                    emit("objects-changed");
                    emit("open-object", { typeUrl: "tables", name: request.name });
                    setStatus("Tabla \"" + request.name + "\" creada en " + state.database + ".", "ok");
                }
            } catch (err) {
                setStatus(err.message, "error");
            }
        });
    }

    $("ct-add").addEventListener("click", () => addColumn().name.focus());
    $("ct-preview").addEventListener("click", (event) => send(false, event.target));
    $("ct-form").addEventListener("submit", (event) => {
        event.preventDefault();
        send(true, $("ct-create"));
    });

    reset();
}

function initViewForm() {
    function readRequest(execute) {
        return {
            name: $("cv-name").value.trim(),
            select: $("cv-select").value,
            orReplace: $("cv-replace").checked,
            execute
        };
    }

    async function send(execute, button) {
        if (!requireDatabase()) return;
        clearStatus();
        await withBusy(button, async () => {
            try {
                const request = readRequest(execute);
                const result = await post(dbPath() + "/create/view", request);
                $("cv-sql").textContent = result.sql;
                $("cv-sql").hidden = false;
                if (result.executed) {
                    $("cv-form").reset();
                    emit("objects-changed");
                    emit("open-object", { typeUrl: "views", name: request.name });
                    setStatus("Vista \"" + request.name + "\" creada en " + state.database + ".", "ok");
                }
            } catch (err) {
                setStatus(err.message, "error");
            }
        });
    }

    $("cv-preview").addEventListener("click", (event) => send(false, event.target));
    $("cv-form").addEventListener("submit", (event) => {
        event.preventDefault();
        send(true, $("cv-create"));
    });
}
