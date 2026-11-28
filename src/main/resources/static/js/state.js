export const state = {
    conn: null,
    database: null,
    types: []
};

const bus = new EventTarget();

export function emit(name, detail) {
    bus.dispatchEvent(new CustomEvent(name, { detail }));
}

export function on(name, handler) {
    bus.addEventListener(name, (event) => handler(event.detail));
}

export function dbPath() {
    return "/connections/" + encodeURIComponent(state.conn.id) +
        "/databases/" + encodeURIComponent(state.database);
}
