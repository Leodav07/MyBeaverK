/*
 * Cómo se comunica esta página con tu API:
 *
 * 1. El usuario pulsa "Conectar". El evento "submit" lo intercepta este script;
 *    preventDefault() evita que el navegador recargue la página.
 * 2. Se arma un objeto con los campos del formulario. Sus nombres (host, port,
 *    username, password) deben ser idénticos a los de ConnectionInfodto. Si uno
 *    no coincide, Spring no avisa y el valor llega vacío o falla al leer el JSON.
 * 3. fetch() envía ese objeto como JSON por POST a /api/conn/connect y espera
 *    la respuesta (por eso la función es async y usa await).
 * 4. El servidor responde con un código HTTP y un cuerpo. fetch NO lanza error
 *    con códigos 4xx o 5xx; solo falla si no hay conexión con el servidor. Por
 *    eso se revisa response.ok.
 * 5. Si todo salió bien, el cuerpo es { ok, msg, data } y data es la lista de
 *    bases. Se dibuja con textContent (nunca innerHTML), para que ningún nombre
 *    pueda ejecutar código dentro de la página.
 */
(() => {
    "use strict";

    const API_URL = "/api/conn/connect";

    const form = document.getElementById("connection-form");
    const button = document.getElementById("connect-btn");
    const statusBox = document.getElementById("status");
    const dbList = document.getElementById("db-list");
    const emptyHint = document.getElementById("results-empty");
    const countLabel = document.getElementById("results-count");

    function setStatus(message, kind) {
        statusBox.textContent = message;
        statusBox.className = "status" + (kind ? " is-" + kind : "");
        statusBox.hidden = false;
    }

    function setBusy(busy) {
        button.disabled = busy;
        button.textContent = busy ? "Conectando…" : "Conectar";
    }

    function clearDatabases() {
        dbList.replaceChildren();
        dbList.hidden = true;
        countLabel.hidden = true;
        emptyHint.hidden = false;
    }

    function renderDatabases(names) {
        const items = names.map((name) => {
            const li = document.createElement("li");
            li.textContent = name;
            return li;
        });
        dbList.replaceChildren(...items);
        dbList.hidden = false;
        emptyHint.hidden = true;
        countLabel.textContent =
            names.length + (names.length === 1 ? " base de datos" : " bases de datos");
        countLabel.hidden = false;
    }

    function readForm() {
        return {
            host: form.host.value.trim(),
            port: Number.parseInt(form.port.value, 10), // se envía como número, no como texto
            username: form.username.value.trim(),
            password: form.password.value
        };
    }

    // Validación en el navegador: ahorra una petición, pero el servidor debe validar también.
    function validate(data) {
        if (!data.host) return "Escribe el servidor (por ejemplo 127.0.0.1).";
        if (!Number.isInteger(data.port) || data.port < 1 || data.port > 65535) {
            return "El puerto debe ser un número entre 1 y 65535.";
        }
        if (!data.username) return "Escribe el usuario.";
        return null;
    }

    // Algunos errores del servidor llegan como texto plano y otros como JSON.
    async function readBody(response) {
        const text = await response.text();
        try {
            return JSON.parse(text);
        } catch {
            return { ok: false, msg: text || null, data: null };
        }
    }

    function fallbackMessage(status) {
        if (status === 400) return "Los datos enviados no son válidos.";
        if (status >= 500) return "El servidor no pudo completar la conexión.";
        return "No se pudo conectar (código " + status + ").";
    }

    async function connect(data) {
        const response = await fetch(API_URL, {
            method: "POST",
            headers: { "Content-Type": "application/json", "Accept": "application/json" },
            body: JSON.stringify(data)
        });
        const body = await readBody(response);

        if (response.ok && body.ok === true && Array.isArray(body.data)) {
            return body.data;
        }
        throw new Error(body.msg || fallbackMessage(response.status));
    }

    form.addEventListener("submit", async (event) => {
        event.preventDefault();

        const data = readForm();
        const problem = validate(data);
        if (problem) {
            setStatus(problem, "error");
            return;
        }

        setBusy(true);
        setStatus("Conectando…", "");

        try {
            const names = await connect(data);
            renderDatabases(names);
            setStatus("Conectado a " + data.host + ":" + data.port + ".", "ok");
        } catch (err) {
            clearDatabases();
            // TypeError = fetch no llegó al servidor (aplicación apagada o sin red)
            const unreachable = err instanceof TypeError;
            setStatus(
                unreachable
                    ? "No se pudo contactar con la aplicación. Revisa que esté en marcha."
                    : err.message,
                "error"
            );
        } finally {
            setBusy(false);
        }
    });
})();