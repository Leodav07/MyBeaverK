import { post, el, enc, buildGrid, downloadText, withBusy } from "./api.js";
import { state, emit, on } from "./state.js";
import { selectTab, setStatus, clearStatus } from "./workspace.js";

const $ = (id) => document.getElementById(id);

export function initSqlEditor() {
    const input = $("sql-input");
    const runBtn = $("sql-run");
    const results = $("sql-results");

    function scriptToRun() {
        const { selectionStart: a, selectionEnd: b, value } = input;
        return a !== b ? value.slice(a, b) : value;
    }

    async function execute(sql) {
        if (!state.conn) return;
        if (!sql.trim()) {
            setStatus("Escribe alguna sentencia SQL.", "error");
            return;
        }
        clearStatus();
        results.replaceChildren(el("p", { className: "hint" }, "Ejecutando…"));
        const maxRows = Number.parseInt($("sql-max").value, 10) || 500;

        await withBusy(runBtn, async () => {
            try {
                const data = await post("/connections/" + enc(state.conn.id) + "/sql",
                    { sql, database: state.database, maxRows });
                renderResults(data);
                if (data.results.some((r) => r.kind !== "RESULT_SET")) emit("objects-changed");
            } catch (err) {
                results.replaceChildren();
                setStatus(err.message, "error");
            }
        });
    }

    function renderResults(data) {
        const ok = data.results.filter((r) => r.kind !== "ERROR").length;
        const summary = data.failed
            ? `Se ejecutaron ${ok} resultado(s) y luego hubo un error (las sentencias anteriores sí quedaron aplicadas).`
            : `${ok} resultado(s) · ${data.totalMs} ms`;
        setStatus(summary, data.failed ? "error" : "ok");

        results.replaceChildren(...data.results.map((r) => {
            const head = el("div", { className: "result-head" },
                el("span", { className: "badge" }, "#" + r.index),
                el("code", { className: "result-sql" }, r.sql),
                r.elapsedMs ? el("span", { className: "hint small" }, r.elapsedMs + " ms") : null);

            let body;
            if (r.kind === "ERROR") {
                body = el("p", { className: "error-text" }, r.message);
            } else if (r.kind === "UPDATE") {
                body = el("p", { className: "ok-text" },
                    r.updateCount === 1 ? "1 fila afectada." : r.updateCount + " filas afectadas.");
            } else {
                const note = r.rows.length + (r.rows.length === 1 ? " fila" : " filas") +
                    (r.truncated ? " (se muestran solo las primeras; sube «Máx. filas» para ver más)" : "");
                body = el("div", {},
                    el("div", { className: "grid-wrap" }, buildGrid(r.columns, r.rows)),
                    el("p", { className: "hint small" }, note));
            }
            return el("section", { className: "result" + (r.kind === "ERROR" ? " is-error" : "") }, head, body);
        }));
    }

    runBtn.addEventListener("click", () => execute(scriptToRun()));

    input.addEventListener("keydown", (event) => {
        if ((event.ctrlKey || event.metaKey) && event.key === "Enter") {
            event.preventDefault();
            execute(scriptToRun());
        }
        if (event.key === "Tab" && !event.shiftKey) {
            event.preventDefault();
            const { selectionStart: a, selectionEnd: b } = input;
            input.setRangeText("    ", a, b, "end");
        }
    });

    $("sql-clear").addEventListener("click", () => {
        input.value = "";
        results.replaceChildren();
        clearStatus();
        input.focus();
    });

    $("sql-export").addEventListener("click", () => {
        downloadText("script.sql", input.value + "\n");
    });

    $("sql-open").addEventListener("click", () => $("sql-file").click());
    $("sql-file").addEventListener("change", async (event) => {
        const file = event.target.files[0];
        if (!file) return;
        input.value = await file.text();
        event.target.value = "";
        setStatus("Archivo \"" + file.name + "\" cargado. Pulsa «Ejecutar» para correrlo.", "");
    });

    on("open-sql", ({ sql, run }) => {
        selectTab("sql");
        input.value = sql;
        results.replaceChildren();
        input.focus();
        if (run) execute(sql);
    });

    on("database-changed", (name) => {
        $("sql-db-label").textContent = name || "(ninguna)";
    });
}
