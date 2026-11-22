/*
 * Página de conexiones de MyBeaver.
 * Todas las llamadas a la API pasan por request(), que devuelve el campo "data"
 * de la respuesta { ok, msg, data } o lanza un Error con el mensaje del servidor.
 * Todo texto que viene del servidor se escribe con textContent, nunca con innerHTML.
 */
(() => {
    "use strict";

    const $ = (id) => document.getElementById(id);
    const form = $("connection-form");
    const connectBtn = $("connect-btn");
    const saveBtn = $("save-btn");
    const statusBox = $("status");
    const savedList = $("saved-list");
    const savedEmpty = $("saved-empty");
    const dbList = $("db-list");
    const dbEmpty = $("results-empty");
    const dbCount = $("results-count");

    function fallbackMessage(status) {
        if (status === 400) return "Los datos enviados no son válidos.";
        if (status === 404) return "No se encontró lo que se pidió.";
        if (status === 409) return "Ese registro ya existe.";
        if (status >= 500) return "El servidor no pudo completar la operación.";
        return "La operación falló (código " + status + ").";
    }

    async function request(path, options = {}) {
        const headers = { "Accept": "application/json" };
        if (options.body) headers["Content-Type"] = "application/json";

        const response = await fetch("/api" + path, { ...options, headers });
        const text = await response.text();

        let body = null;
        try {
            body = text ? JSON.parse(text) : null;   // un 204 llega sin cuerpo
        } catch {
            body = { ok: false, msg: text || null, data: null };
        }

        if (!response.ok || (body && body.ok === false)) {
            throw new Error((body && body.msg) || fallbackMessage(response.status));
        }
        return body ? body.data : null;
    }

    function setStatus(message, kind) {
        statusBox.textContent = message;
        statusBox.className = "status" + (kind ? " is-" + kind : "");
        statusBox.hidden = false;
    }

    function setBusy(busy) {
        document.querySelectorAll("button").forEach((b) => { b.disabled = busy; });
        connectBtn.textContent = busy ? "Conectando…" : "Probar conexión";
    }

    function showError(err) {
        const unreachable = err instanceof TypeError;   // fetch no llegó al servidor
        setStatus(
            unreachable ? "No se pudo contactar con la aplicación. Revisa que esté en marcha." : err.message,
            "error"
        );
    }

    /* ---------- Bases de datos ---------- */

    function clearDatabases() {
        dbList.replaceChildren();
        dbList.hidden = true;
        dbCount.hidden = true;
        dbEmpty.hidden = false;
    }

    function renderDatabases(names, label) {
        dbList.replaceChildren(...names.map((name) => {
            const li = document.createElement("li");
            li.textContent = name;
            return li;
        }));
        dbList.hidden = false;
        dbEmpty.hidden = true;
        dbCount.textContent =
            names.length + (names.length === 1 ? " base de datos" : " bases de datos") + " en " + label;
        dbCount.hidden = false;
    }

    /* ---------- Conexiones guardadas ---------- */

    function makeButton(text, action, id, extraClass) {
        const b = document.createElement("button");
        b.type = "button";
        b.textContent = text;
        b.dataset.action = action;
        b.dataset.id = id;
        if (extraClass) b.className = extraClass;
        return b;
    }

    function renderSaved(items) {
        savedList.replaceChildren(...items.map((c) => {
            const li = document.createElement("li");
            li.dataset.label = c.name;

            const info = document.createElement("div");
            info.className = "saved-info";
            const name = document.createElement("div");
            name.className = "saved-name";
            name.textContent = c.name;
            const meta = document.createElement("div");
            meta.className = "saved-meta";
            meta.textContent = c.username + "@" + c.host + ":" + c.port;
            info.append(name, meta);

            li.append(info, makeButton("Conectar", "connect", c.id), makeButton("Borrar", "delete", c.id, "danger"));
            return li;
        }));
        savedList.hidden = items.length === 0;
        savedEmpty.hidden = items.length !== 0;
    }

    async function loadSaved() {
        renderSaved((await request("/connections")) || []);
    }

    /* ---------- Formulario ---------- */

    function readForm() {
        return {
            name: form.name.value.trim(),
            host: form.host.value.trim(),
            port: Number.parseInt(form.port.value, 10),
            username: form.username.value.trim(),
            password: form.password.value
        };
    }

    function validate(data, needName) {
        if (needName && !data.name) return "Escribe un nombre para guardar la conexión.";
        if (!data.host) return "Escribe el servidor (por ejemplo 127.0.0.1).";
        if (!Number.isInteger(data.port) || data.port < 1 || data.port > 65535) {
            return "El puerto debe ser un número entre 1 y 65535.";
        }
        if (!data.username) return "Escribe el usuario.";
        return null;
    }

    async function run(task) {
        setBusy(true);
        try {
            await task();
        } catch (err) {
            showError(err);
        } finally {
            setBusy(false);
        }
    }

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const data = readForm();
        const problem = validate(data, false);
        if (problem) return setStatus(problem, "error");

        run(async () => {
            setStatus("Conectando…", "");
            const { host, port, username, password } = data;
            try {
                const names = await request("/conn/connect", {
                    method: "POST",
                    body: JSON.stringify({ host, port, username, password })
                });
                renderDatabases(names, host + ":" + port);
                setStatus("Conectado a " + host + ":" + port + ".", "ok");
            } catch (err) {
                clearDatabases();
                throw err;
            }
        });
    });

    saveBtn.addEventListener("click", () => {
        const data = readForm();
        const problem = validate(data, true);
        if (problem) return setStatus(problem, "error");

        run(async () => {
            await request("/connections", { method: "POST", body: JSON.stringify(data) });
            form.reset();
            await loadSaved();
            setStatus("Conexión \"" + data.name + "\" guardada.", "ok");
        });
    });

    savedList.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const id = encodeURIComponent(button.dataset.id);
        const label = button.closest("li").dataset.label;

        if (button.dataset.action === "connect") {
            run(async () => {
                setStatus("Conectando con \"" + label + "\"…", "");
                try {
                    const names = await request("/connections/" + id + "/connect", { method: "POST" });
                    renderDatabases(names, label);
                    setStatus("Conectado a \"" + label + "\".", "ok");
                } catch (err) {
                    clearDatabases();
                    throw err;
                }
            });
        } else if (button.dataset.action === "delete") {
            if (!window.confirm("¿Borrar la conexión \"" + label + "\"?")) return;
            run(async () => {
                await request("/connections/" + id, { method: "DELETE" });
                await loadSaved();
                setStatus("Conexión \"" + label + "\" borrada.", "ok");
            });
        }
    });

    loadSaved().catch(showError);
})();