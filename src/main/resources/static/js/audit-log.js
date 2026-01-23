const auditBody = document.getElementById("audit-log-body");
const auditEmpty = document.getElementById("audit-empty");
const auditFilters = document.getElementById("audit-filters");
const auditSearch = document.getElementById("audit-search");
const auditActor = document.getElementById("audit-actor");
const auditAction = document.getElementById("audit-action");
const auditSize = document.getElementById("audit-size");
const auditClear = document.getElementById("audit-clear");
const auditPaginationInfo = document.getElementById("audit-pagination-info");
const auditPrev = document.getElementById("audit-prev");
const auditNext = document.getElementById("audit-next");

const DEFAULT_PAGE_SIZE = 25;
const state = {
    page: 0,
    size: DEFAULT_PAGE_SIZE,
    search: "",
    actor: "",
    action: "",
    totalPages: 0,
    totalElements: 0
};

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

const updatePagination = () => {
    const totalPages = state.totalPages || 0;
    const totalElements = state.totalElements || 0;
    if (totalElements === 0) {
        auditPaginationInfo.textContent = "No matching audit entries.";
        auditPrev.disabled = true;
        auditNext.disabled = true;
        return;
    }
    const currentPage = totalPages === 0 ? 0 : state.page + 1;
    auditPaginationInfo.textContent = `Page ${currentPage} of ${totalPages} · ${totalElements} entries`;
    auditPrev.disabled = state.page <= 0;
    auditNext.disabled = totalPages === 0 || state.page >= totalPages - 1;
};

const buildQueryParams = () => {
    const params = new URLSearchParams();
    if (state.search) {
        params.set("search", state.search);
    }
    if (state.actor) {
        params.set("actor", state.actor);
    }
    if (state.action) {
        params.set("action", state.action);
    }
    params.set("page", state.page.toString());
    params.set("size", state.size.toString());
    return params.toString();
};

const loadAuditLog = () =>
    fetch(`/api/channels/${encodeURIComponent(broadcaster)}/audit?${buildQueryParams()}`)
        .then((response) => {
            if (!response.ok) {
                throw new Error(`Failed to load audit log (${response.status})`);
            }
            return response.json();
        })
        .then((payload) => {
            const entries = payload.entries || [];
            state.page = payload.page ?? state.page;
            state.size = payload.size ?? state.size;
            state.totalPages = payload.totalPages ?? 0;
            state.totalElements = payload.totalElements ?? 0;
            renderEntries(entries);
            updatePagination();
        })
        .catch((error) => {
            console.error(error);
            auditEmpty.textContent = "Unable to load audit entries.";
            auditEmpty.classList.remove("hidden");
            auditPaginationInfo.textContent = "Unable to load audit entries.";
        });

const applyFilters = () => {
    state.page = 0;
    state.search = auditSearch.value.trim();
    state.actor = auditActor.value.trim();
    state.action = auditAction.value.trim();
    state.size = Number.parseInt(auditSize.value, 10) || DEFAULT_PAGE_SIZE;
    loadAuditLog();
};

auditFilters.addEventListener("submit", (event) => {
    event.preventDefault();
    applyFilters();
});

auditClear.addEventListener("click", () => {
    auditSearch.value = "";
    auditActor.value = "";
    auditAction.value = "";
    auditSize.value = DEFAULT_PAGE_SIZE.toString();
    applyFilters();
});

auditPrev.addEventListener("click", () => {
    if (state.page > 0) {
        state.page -= 1;
        loadAuditLog();
    }
});

auditNext.addEventListener("click", () => {
    if (state.totalPages && state.page < state.totalPages - 1) {
        state.page += 1;
        loadAuditLog();
    }
});

loadAuditLog();
