import { initConnections } from "./connection.js";
import { initWorkspace, openWorkspace } from "./workspace.js";
import { initSqlEditor } from "./sql-editor.js";
import { initCreateForms } from "./create.js";

const connectionsView = document.getElementById("view-connections");
const workspaceView = document.getElementById("view-workspace");

initWorkspace(() => {
    workspaceView.hidden = true;
    connectionsView.hidden = false;
});
initSqlEditor();
initCreateForms();

initConnections(async (conn) => {
    await openWorkspace(conn);
    connectionsView.hidden = true;
    workspaceView.hidden = false;
});
