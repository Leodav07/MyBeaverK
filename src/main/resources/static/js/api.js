function fallbackMessage(status) {
    if (status === 400) return "Los datos enviados no son válidos.";
    if (status === 403) return "Tu usuario no tiene privilegios para esto.";
    if (status === 404) return "No se encontró lo que se pidió.";
    if (status === 409) return "Ese registro ya existe.";
    if (status >= 500) return "El servidor no pudo completar la operación.";
    return "La operación falló (código " + status + ").";
}

export async function request(path, options = {}) {
    const headers = { "Accept": "application/json" };
    if (options.body) headers["Content-Type"] = "application/json";

    let response;
    try {
        response = await fetch("/api" + path, { ...options, headers });
    } catch {
        throw new Error("No se pudo contactar con la aplicación. Revisa que esté en marcha.");
    }
    const text = await response.text();

    let body = null;
    try {
        body = text ? JSON.parse(text) : null;
    } catch {
        body = { ok: false, msg: null, data: null };
    }

    if (!response.ok || (body && body.ok === false)) {
        const error = new Error((body && body.msg) || fallbackMessage(response.status));
        error.status = response.status;
        throw error;
    }
    return body ? body.data : null;
}

export const post = (path, data) => request(path, { method: "POST", body: JSON.stringify(data ?? {}) });

export const enc = encodeURIComponent;

export function el(tag, props = {}, ...children) {
    const node = document.createElement(tag);
    for (const [key, value] of Object.entries(props)) {
        if (key === "dataset") Object.assign(node.dataset, value);
        else if (key in node) node[key] = value;
        else node.setAttribute(key, value);
    }
    for (const child of children) {
        if (child == null || child === false) continue;
        node.append(child instanceof Node ? child : String(child));
    }
    return node;
}

export function buildGrid(headers, rows) {
    const thead = el("thead", {}, el("tr", {}, ...headers.map((h) => el("th", {}, h))));
    const tbody = el("tbody", {}, ...rows.map((row) =>
        el("tr", {}, ...row.map((cell) =>
            cell === null || cell === undefined
                ? el("td", { className: "null" }, "NULL")
                : el("td", {}, cell)))));
    return el("table", { className: "grid" }, thead, tbody);
}

export const ident = (name) => "`" + String(name).replace(/`/g, "``") + "`";
export const literal = (text) => "'" + String(text).replace(/\\/g, "\\\\").replace(/'/g, "''") + "'";

export function downloadText(fileName, text) {
    const blob = new Blob([text], { type: "application/sql;charset=utf-8" });
    const url = URL.createObjectURL(blob);
    const a = el("a", { href: url, download: fileName });
    document.body.append(a);
    a.click();
    a.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
}

export function safeFileName(name) {
    return String(name).replace(/[^\w.@-]+/g, "_") || "script";
}

export function showStatus(box, message, kind = "") {
    box.textContent = message;
    box.className = "status" + (kind ? " is-" + kind : "");
    box.hidden = false;
}

export async function withBusy(button, task) {
    const previous = button.disabled;
    button.disabled = true;
    try {
        return await task();
    } finally {
        button.disabled = previous;
    }
}
