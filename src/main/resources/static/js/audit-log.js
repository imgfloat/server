const auditBody = document.getElementById("audit-log-body");
const auditEmpty = document.getElementById("audit-empty");

const formatTimestamp = (value) => {
    if (!value) {
        return "";
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
        return value;
    }
    return date.toLocaleString();
};

const renderEntries = (entries) => {
    auditBody.innerHTML = "";
    if (!entries || entries.length === 0) {
        auditEmpty.classList.remove("hidden");
        return;
    }
    auditEmpty.classList.add("hidden");
    entries.forEach((entry) => {
        const row = document.createElement("tr");

        const timeCell = document.createElement("td");
        timeCell.textContent = formatTimestamp(entry.createdAt);
        row.appendChild(timeCell);

        const actorCell = document.createElement("td");
        actorCell.textContent = entry.actor || "system";
        row.appendChild(actorCell);

        const actionCell = document.createElement("td");
        actionCell.textContent = entry.action;
        row.appendChild(actionCell);

        const detailCell = document.createElement("td");
        detailCell.textContent = entry.details || "";
        row.appendChild(detailCell);

        auditBody.appendChild(row);
    });
};

const loadAuditLog = () =>
    fetch(`/api/channels/${encodeURIComponent(broadcaster)}/audit`)
        .then((response) => {
            if (!response.ok) {
                throw new Error(`Failed to load audit log (${response.status})`);
            }
            return response.json();
        })
        .then((entries) => {
            renderEntries(entries);
        })
        .catch((error) => {
            console.error(error);
            auditEmpty.textContent = "Unable to load audit entries.";
            auditEmpty.classList.remove("hidden");
        });

loadAuditLog();
