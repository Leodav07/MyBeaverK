import { request, post, el, showStatus } from "./api.js";

const $ = (id) => document.getElementById(id);

export function initConnections(onOpen) {
    const form = $("connection-form");
    const connectBtn = $("connect-btn");
    const statusBox = $("status");
    const savedList = $("saved-list");
    const savedEmpty = $("saved-empty");

    const setStatus = (message, kind) => showStatus(statusBox, message, kind);

    function setBusy(busy) {
        document.querySelectorAll("#view-connections button").forEach((b) => { b.disabled = busy; });
        connectBtn.textContent = busy ? "Conectando…" : "Conectar";
    }

    async function run(task) {
        setBusy(true);
        try {
            await task();
        } catch (err) {
            setStatus(err.message, "error");
        } finally {
            setBusy(false);
        }
    }

    function makeButton(text, action, id, extraClass) {
        return el("button", { type: "button", className: extraClass || "", dataset: { action, id } }, text);
    }

    function renderSaved(items) {
        savedList.replaceChildren(...items.map((c) => {
            const info = el("div", { className: "saved-info" },
                el("div", { className: "saved-name" }, c.name),
                el("div", { className: "saved-meta" }, c.username + "@" + c.host + ":" + c.port));
            const li = el("li", { dataset: { label: c.name } }, info,
                makeButton("Conectar", "connect", c.id),
                makeButton("Borrar", "delete", c.id, "danger"));
            return li;
        }));
        savedList.hidden = items.length === 0;
        savedEmpty.hidden = items.length !== 0;
    }

    async function loadSaved() {
        renderSaved((await request("/connections")) || []);
    }

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

    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const data = readForm();
        const problem = validate(data, false);
        if (problem) return setStatus(problem, "error");

        run(async () => {
            setStatus("Conectando…", "");
            const { host, port, username, password } = data;
            const session = await post("/connections/session", { host, port, username, password });
            form.password.value = "";
            statusBox.hidden = true;
            await onOpen({ id: session.id, name: session.name, temporary: true, databases: session.databases });
        });
    });

    $("save-btn").addEventListener("click", () => {
        const data = readForm();
        const problem = validate(data, true);
        if (problem) return setStatus(problem, "error");

        run(async () => {
            await post("/connections", data);
            form.reset();
            form.port.value = "3306";
            await loadSaved();
            setStatus("Conexión \"" + data.name + "\" guardada.", "ok");
        });
    });

    savedList.addEventListener("click", (event) => {
        const button = event.target.closest("button[data-action]");
        if (!button) return;
        const id = button.dataset.id;
        const label = button.closest("li").dataset.label;

        if (button.dataset.action === "connect") {
            run(async () => {
                setStatus("Conectando con \"" + label + "\"…", "");
                const databases = await request("/connections/" + encodeURIComponent(id) + "/connect", { method: "POST" });
                statusBox.hidden = true;
                await onOpen({ id, name: label, temporary: false, databases });
            });
        } else if (button.dataset.action === "delete") {
            if (!window.confirm("¿Borrar la conexión \"" + label + "\"?")) return;
            run(async () => {
                await request("/connections/" + encodeURIComponent(id), { method: "DELETE" });
                await loadSaved();
                setStatus("Conexión \"" + label + "\" borrada.", "ok");
            });
        }
    });

    loadSaved().catch((err) => setStatus(err.message, "error"));
}
