export function createCustomAssetModal({
    broadcaster,
    adminChannels = [],
    showToast = globalThis.showToast,
    onAssetSaved,
}) {
    const launchModal = document.getElementById("custom-asset-launch-modal");
    const launchNewButton = document.getElementById("custom-asset-launch-new");
    const launchMarketplaceButton = document.getElementById("custom-asset-launch-marketplace");
    const marketplaceModal = document.getElementById("custom-asset-marketplace-modal");
    const marketplaceCloseButton = document.getElementById("custom-asset-marketplace-close");
    const marketplaceSearchInput = document.getElementById("custom-asset-marketplace-search");
    const marketplaceList = document.getElementById("custom-asset-marketplace-list");
    const marketplaceChannelSelect = document.getElementById("custom-asset-marketplace-channel");
    const assetModal = document.getElementById("custom-asset-modal");
    const userNameInput = document.getElementById("custom-asset-name");
    const descriptionInput = document.getElementById("custom-asset-description");
    const publicCheckbox = document.getElementById("custom-asset-public");
    const logoInput = document.getElementById("custom-asset-logo-file");
    const logoPreview = document.getElementById("custom-asset-logo-preview");
    const logoClearButton = document.getElementById("custom-asset-logo-clear");
    const userSourceTextArea = document.getElementById("custom-asset-code");
    const formErrorWrapper = document.getElementById("custom-asset-error");
    const jsErrorTitle = document.getElementById("js-error-title");
    const jsErrorDetails = document.getElementById("js-error-details");
    const form = document.getElementById("custom-asset-form");
    const cancelButton = document.getElementById("custom-asset-cancel");
    const attachmentInput = document.getElementById("custom-asset-attachment-file");
    const attachmentList = document.getElementById("custom-asset-attachment-list");
    const attachmentHint = document.getElementById("custom-asset-attachment-hint");
    let currentAssetId = null;
    let pendingLogoFile = null;
    let logoRemoved = false;
    let attachmentState = [];
    let marketplaceEntries = [];

    const resetErrors = () => {
        if (formErrorWrapper) {
            formErrorWrapper.classList.add("hidden");
        }
        if (jsErrorTitle) {
            jsErrorTitle.textContent = "";
        }
        if (jsErrorDetails) {
            jsErrorDetails.textContent = "";
        }
    };

    const openLaunchModal = () => {
        launchModal?.classList.remove("hidden");
    };

    const closeLaunchModal = () => {
        launchModal?.classList.add("hidden");
    };

    const openMarketplaceModal = () => {
        closeLaunchModal();
        marketplaceModal?.classList.remove("hidden");
        if (marketplaceChannelSelect) {
            marketplaceChannelSelect.value = broadcaster?.toLowerCase() || marketplaceChannelSelect.value;
        }
        if (marketplaceSearchInput) {
            marketplaceSearchInput.value = "";
        }
        loadMarketplace();
    };

    const closeMarketplaceModal = () => {
        marketplaceModal?.classList.add("hidden");
    };

    const openModal = () => {
        assetModal?.classList.remove("hidden");
    };

    const closeModal = () => {
        assetModal?.classList.add("hidden");
    };

    const openNew = () => {
        closeLaunchModal();
        if (userNameInput) {
            userNameInput.value = "";
        }
        if (descriptionInput) {
            descriptionInput.value = "";
        }
        if (publicCheckbox) {
            publicCheckbox.checked = false;
        }
        resetLogoState();
        if (userSourceTextArea) {
            userSourceTextArea.value = "";
            userSourceTextArea.disabled = false;
            userSourceTextArea.dataset.assetId = "";
            userSourceTextArea.placeholder =
                "function init(context, state) {\n  const { assets } = context;\n\n}\n\nfunction tick(context, state) {\n\n}\n\n// or\n// module.exports.init = (context, state) => {};\n// module.exports.tick = (context, state) => {};";
        }
        setAttachmentState(null, []);
        resetErrors();
        openModal();
    };

    const openEditor = (asset) => {
        if (!asset) {
            return;
        }
        resetErrors();
        if (userNameInput) {
            userNameInput.value = asset.name || "";
        }
        if (descriptionInput) {
            descriptionInput.value = asset.description || "";
        }
        if (publicCheckbox) {
            publicCheckbox.checked = !!asset.isPublic;
        }
        resetLogoState();
        if (logoPreview && asset.logoUrl) {
            const img = document.createElement("img");
            img.src = asset.logoUrl;
            img.alt = asset.name || "Script logo";
            logoPreview.appendChild(img);
        }
        if (userSourceTextArea) {
            userSourceTextArea.value = "";
            userSourceTextArea.placeholder = "Loading script...";
            userSourceTextArea.disabled = true;
            userSourceTextArea.dataset.assetId = asset.id;
        }
        setAttachmentState(asset.id, asset.scriptAttachments || []);
        openModal();

        fetch(asset.url)
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Failed to load script");
                }
                return response.text();
            })
            .then((text) => {
                if (userSourceTextArea) {
                    userSourceTextArea.disabled = false;
                    userSourceTextArea.value = text;
                }
            })
            .catch(() => {
                if (userSourceTextArea) {
                    userSourceTextArea.disabled = false;
                    userSourceTextArea.value = "";
                }
                showToast?.("Unable to load script content.", "error");
            });
    };

    const handleFormSubmit = (formEvent) => {
        formEvent.preventDefault();
        const src = userSourceTextArea?.value;
        const error = getUserJavaScriptSourceError(src);
        if (error) {
            if (jsErrorTitle) {
                jsErrorTitle.textContent = error.title;
            }
            if (jsErrorDetails) {
                jsErrorDetails.textContent = error.details;
            }
            if (formErrorWrapper) {
                formErrorWrapper.classList.remove("hidden");
            }
            return false;
        }
        resetErrors();
        const name = userNameInput?.value?.trim();
        if (!name) {
            if (jsErrorTitle) {
                jsErrorTitle.textContent = "Missing name";
            }
            if (jsErrorDetails) {
                jsErrorDetails.textContent = "Please provide a name for your custom asset.";
            }
            if (formErrorWrapper) {
                formErrorWrapper.classList.remove("hidden");
            }
            return false;
        }
        const assetId = userSourceTextArea?.dataset?.assetId;
        const description = descriptionInput?.value?.trim();
        const isPublic = !!publicCheckbox?.checked;
        const submitButton = formEvent.currentTarget?.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Saving...";
        }
        saveCodeAsset({ name, src, assetId, description, isPublic })
            .then((asset) => {
                if (asset) {
                    return syncLogoChanges(asset).then((updated) => {
                        onAssetSaved?.(updated || asset);
                        return updated || asset;
                    });
                }
                return null;
            })
            .then((asset) => {
                closeModal();
                if (asset) {
                    showToast?.(assetId ? "Custom asset updated." : "Custom asset created.", "success");
                }
            })
            .catch((e) => {
                showToast?.("Unable to save custom asset. Please try again.", "error");
                console.error(e);
            })
            .finally(() => {
                if (submitButton) {
                    submitButton.disabled = false;
                    submitButton.textContent = "Test and save";
                }
            });
        return false;
    };

    if (launchModal) {
        launchModal.addEventListener("click", (event) => {
            if (event.target === launchModal) {
                closeLaunchModal();
            }
        });
    }
    if (launchNewButton) {
        launchNewButton.addEventListener("click", () => openNew());
    }
    if (launchMarketplaceButton) {
        launchMarketplaceButton.addEventListener("click", () => openMarketplaceModal());
    }
    if (marketplaceModal) {
        marketplaceModal.addEventListener("click", (event) => {
            if (event.target === marketplaceModal) {
                closeMarketplaceModal();
            }
        });
    }
    if (marketplaceCloseButton) {
        marketplaceCloseButton.addEventListener("click", () => closeMarketplaceModal());
    }
    if (assetModal) {
        assetModal.addEventListener("click", (event) => {
            if (event.target === assetModal) {
                closeModal();
            }
        });
    }
    if (form) {
        form.addEventListener("submit", handleFormSubmit);
    }
    if (cancelButton) {
        cancelButton.addEventListener("click", () => closeModal());
    }
    if (logoInput) {
        logoInput.addEventListener("change", (event) => {
            const file = event.target?.files?.[0];
            if (!file) {
                return;
            }
            pendingLogoFile = file;
            logoRemoved = false;
            renderLogoPreview(file);
        });
    }
    if (logoClearButton) {
        logoClearButton.addEventListener("click", () => {
            logoRemoved = true;
            pendingLogoFile = null;
            if (logoInput) {
                logoInput.value = "";
            }
            clearLogoPreview();
        });
    }
    if (marketplaceSearchInput) {
        const handler = debounce((event) => {
            loadMarketplace(event.target?.value);
        }, 250);
        marketplaceSearchInput.addEventListener("input", handler);
    }
    if (marketplaceChannelSelect) {
        buildChannelOptions();
    }
    if (attachmentInput) {
        attachmentInput.addEventListener("change", (event) => {
            const file = event.target?.files?.[0];
            if (!file) {
                return;
            }
            if (!currentAssetId) {
                showToast?.("Save the script before adding attachments.", "info");
                attachmentInput.value = "";
                return;
            }
            uploadAttachment(file)
                .then((attachment) => {
                    if (attachment) {
                        attachmentState = [...attachmentState, attachment];
                        renderAttachmentList();
                        showToast?.("Attachment added.", "success");
                    }
                })
                .catch((error) => {
                    console.error(error);
                    showToast?.("Unable to upload attachment. Please try again.", "error");
                })
                .finally(() => {
                    attachmentInput.value = "";
                });
        });
    }

    return { openLauncher: openLaunchModal, openNew, openEditor };

    function setAttachmentState(assetId, attachments) {
        currentAssetId = assetId || null;
        attachmentState = Array.isArray(attachments) ? [...attachments] : [];
        renderAttachmentList();
    }

    function renderAttachmentList() {
        if (!attachmentList) {
            return;
        }
        attachmentList.innerHTML = "";
        if (!currentAssetId) {
            if (attachmentInput) {
                attachmentInput.disabled = true;
            }
            if (attachmentHint) {
                attachmentHint.textContent = "Save the script before adding attachments.";
            }
            const empty = document.createElement("li");
            empty.className = "attachment-empty";
            empty.textContent = "Attachments will appear here once the script is saved.";
            attachmentList.appendChild(empty);
            return;
        }
        if (attachmentInput) {
            attachmentInput.disabled = false;
        }
        if (attachmentHint) {
            attachmentHint.textContent =
                "Attachments are available to this script only and are not visible on the canvas.";
        }
        if (!attachmentState.length) {
            const empty = document.createElement("li");
            empty.className = "attachment-empty";
            empty.textContent = "No attachments yet.";
            attachmentList.appendChild(empty);
            return;
        }
        attachmentState.forEach((attachment) => {
            const item = document.createElement("li");
            item.className = "attachment-item";

            const meta = document.createElement("div");
            meta.className = "attachment-meta";
            const name = document.createElement("strong");
            name.textContent = attachment.name || "Untitled";
            const type = document.createElement("span");
            type.textContent = attachment.assetType || attachment.mediaType || "Attachment";
            meta.appendChild(name);
            meta.appendChild(type);

            const actions = document.createElement("div");
            actions.className = "attachment-actions-row";
            if (attachment.url) {
                const link = document.createElement("a");
                link.href = attachment.url;
                link.target = "_blank";
                link.rel = "noopener";
                link.className = "button ghost";
                link.textContent = "Open";
                actions.appendChild(link);
            }
            const remove = document.createElement("button");
            remove.type = "button";
            remove.className = "secondary danger";
            remove.textContent = "Remove";
            remove.addEventListener("click", () => removeAttachment(attachment.id));
            actions.appendChild(remove);

            item.appendChild(meta);
            item.appendChild(actions);
            attachmentList.appendChild(item);
        });
    }

    function uploadAttachment(file) {
        const payload = new FormData();
        payload.append("file", file);
        return fetch(`/api/channels/${encodeURIComponent(broadcaster)}/assets/${currentAssetId}/attachments`, {
            method: "POST",
            body: payload,
        }).then((response) => {
            if (!response.ok) {
                throw new Error("Failed to upload attachment");
            }
            return response.json();
        });
    }

    function removeAttachment(attachmentId) {
        if (!attachmentId || !currentAssetId) {
            return;
        }
        fetch(
            `/api/channels/${encodeURIComponent(broadcaster)}/assets/${currentAssetId}/attachments/${attachmentId}`,
            { method: "DELETE" },
        )
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Failed to delete attachment");
                }
                attachmentState = attachmentState.filter((attachment) => attachment.id !== attachmentId);
                renderAttachmentList();
                showToast?.("Attachment removed.", "success");
            })
            .catch((error) => {
                console.error(error);
                showToast?.("Unable to remove attachment. Please try again.", "error");
            });
    }

    function saveCodeAsset({ name, src, assetId, description, isPublic }) {
        const payload = {
            name,
            source: src,
            description: description || null,
            isPublic,
        };
        const method = assetId ? "PUT" : "POST";
        const url = assetId
            ? `/api/channels/${encodeURIComponent(broadcaster)}/assets/${assetId}/code`
            : `/api/channels/${encodeURIComponent(broadcaster)}/assets/code`;
        return fetch(url, {
            method,
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload),
        }).then((response) => {
            if (!response.ok) {
                throw new Error("Failed to save code asset");
            }
            return response.json();
        });
    }

    function resetLogoState() {
        pendingLogoFile = null;
        logoRemoved = false;
        if (logoInput) {
            logoInput.value = "";
        }
        clearLogoPreview();
    }

    function clearLogoPreview() {
        if (logoPreview) {
            logoPreview.innerHTML = "";
        }
    }

    function renderLogoPreview(file) {
        if (!logoPreview) {
            return;
        }
        clearLogoPreview();
        const img = document.createElement("img");
        img.alt = "Script logo preview";
        if (file instanceof File) {
            const url = URL.createObjectURL(file);
            img.src = url;
            img.onload = () => URL.revokeObjectURL(url);
        }
        logoPreview.appendChild(img);
    }

    function syncLogoChanges(asset) {
        if (!asset?.id) {
            return Promise.resolve(null);
        }
        if (logoRemoved) {
            return fetch(`/api/channels/${encodeURIComponent(broadcaster)}/assets/${asset.id}/logo`, {
                method: "DELETE",
            }).then(() => {
                logoRemoved = false;
                return { ...asset, logoUrl: null };
            });
        }
        if (!pendingLogoFile) {
            return Promise.resolve(null);
        }
        const payload = new FormData();
        payload.append("file", pendingLogoFile);
        return fetch(`/api/channels/${encodeURIComponent(broadcaster)}/assets/${asset.id}/logo`, {
            method: "POST",
            body: payload,
        }).then((response) => {
            if (!response.ok) {
                throw new Error("Failed to upload logo");
            }
            pendingLogoFile = null;
            return response.json();
        });
    }

    function buildChannelOptions() {
        if (!marketplaceChannelSelect) {
            return;
        }
        const channels = [broadcaster, ...adminChannels].filter(Boolean);
        const uniqueChannels = [...new Set(channels.map((channel) => channel.toLowerCase()))];
        marketplaceChannelSelect.innerHTML = "";
        uniqueChannels.forEach((channel) => {
            const option = document.createElement("option");
            option.value = channel;
            option.textContent = channel;
            marketplaceChannelSelect.appendChild(option);
        });
        marketplaceChannelSelect.value = broadcaster?.toLowerCase() || uniqueChannels[0] || "";
    }

    function loadMarketplace(query = "") {
        if (!marketplaceList) {
            return;
        }
        const queryString = query ? `?query=${encodeURIComponent(query)}` : "";
        marketplaceList.innerHTML = '<div class="marketplace-loading">Loading scripts...</div>';
        fetch(`/api/marketplace/scripts${queryString}`)
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Failed to load marketplace");
                }
                return response.json();
            })
            .then((entries) => {
                marketplaceEntries = Array.isArray(entries) ? entries : [];
                renderMarketplace();
            })
            .catch((error) => {
                console.error(error);
                marketplaceList.innerHTML =
                    '<div class="marketplace-empty">Unable to load marketplace scripts.</div>';
            });
    }

    function renderMarketplace() {
        if (!marketplaceList) {
            return;
        }
        marketplaceList.innerHTML = "";
        if (!marketplaceEntries.length) {
            marketplaceList.innerHTML = '<div class="marketplace-empty">No scripts found.</div>';
            return;
        }
        marketplaceEntries.forEach((entry) => {
            const card = document.createElement("div");
            card.className = "marketplace-card";

            if (entry.logoUrl) {
                const logo = document.createElement("img");
                logo.src = entry.logoUrl;
                logo.alt = entry.name || "Script logo";
                logo.className = "marketplace-logo";
                card.appendChild(logo);
            } else {
                const placeholder = document.createElement("div");
                placeholder.className = "marketplace-logo placeholder";
                placeholder.innerHTML = '<i class="fa-solid fa-code"></i>';
                card.appendChild(placeholder);
            }

            const content = document.createElement("div");
            content.className = "marketplace-content";
            const title = document.createElement("strong");
            title.textContent = entry.name || "Untitled script";
            const description = document.createElement("p");
            description.textContent = entry.description || "No description provided.";
            const meta = document.createElement("small");
            meta.textContent = entry.broadcaster ? `By ${entry.broadcaster}` : "";
            content.appendChild(title);
            content.appendChild(description);
            content.appendChild(meta);

            const actions = document.createElement("div");
            actions.className = "marketplace-actions";
            const importButton = document.createElement("button");
            importButton.type = "button";
            importButton.className = "primary";
            importButton.textContent = "Import";
            importButton.addEventListener("click", () => importMarketplaceScript(entry));
            actions.appendChild(importButton);

            card.appendChild(content);
            card.appendChild(actions);
            marketplaceList.appendChild(card);
        });
    }

    function importMarketplaceScript(entry) {
        if (!entry?.id) {
            return;
        }
        const target = marketplaceChannelSelect?.value || broadcaster;
        fetch(`/api/marketplace/scripts/${entry.id}/import`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ targetBroadcaster: target }),
        })
            .then((response) => {
                if (!response.ok) {
                    throw new Error("Failed to import script");
                }
                return response.json();
            })
            .then((asset) => {
                closeMarketplaceModal();
                showToast?.("Script imported.", "success");
                onAssetSaved?.(asset);
            })
            .catch((error) => {
                console.error(error);
                showToast?.("Unable to import script. Please try again.", "error");
            });
    }

    function debounce(fn, wait = 150) {
        let timeout;
        return (...args) => {
            if (timeout) {
                clearTimeout(timeout);
            }
            timeout = setTimeout(() => fn(...args), wait);
        };
    }

    function getUserJavaScriptSourceError(src) {
        let ast;

        const parser = globalThis.acorn;
        if (!parser) {
            return { title: "Parser unavailable", details: "JavaScript parser is not available yet." };
        }

        try {
            ast = parser.parse(src, {
                ecmaVersion: "latest",
                sourceType: "script",
            });
        } catch (e) {
            return { title: "Syntax Error", details: e.message };
        }

        let hasInit = false;
        let hasTick = false;

        const isFunctionNode = (node) =>
            node && (node.type === "FunctionExpression" || node.type === "ArrowFunctionExpression");

        const markFunctionName = (name) => {
            if (name === "init") hasInit = true;
            if (name === "tick") hasTick = true;
        };

        const isModuleExportsMember = (node) =>
            node &&
            node.type === "MemberExpression" &&
            node.object?.type === "Identifier" &&
            node.object.name === "module" &&
            node.property?.type === "Identifier" &&
            node.property.name === "exports";

        const checkObjectExpression = (objectExpression) => {
            if (!objectExpression || objectExpression.type !== "ObjectExpression") {
                return;
            }
            for (const property of objectExpression.properties || []) {
                if (property.type !== "Property") {
                    continue;
                }
                const keyName = property.key?.type === "Identifier" ? property.key.name : property.key?.value;
                if (keyName && isFunctionNode(property.value)) {
                    markFunctionName(keyName);
                }
            }
        };

        for (const node of ast.body) {
            if (node.type === "FunctionDeclaration") {
                markFunctionName(node.id?.name);
                continue;
            }

            if (node.type !== "ExpressionStatement") continue;

            const expr = node.expression;
            if (expr.type !== "AssignmentExpression") continue;

            const left = expr.left;
            const right = expr.right;

            if (left.type === "Identifier" && left.name === "exports" && right.type === "ObjectExpression") {
                checkObjectExpression(right);
                continue;
            }

            if (isModuleExportsMember(left) && right.type === "ObjectExpression") {
                checkObjectExpression(right);
                continue;
            }

            if (left.type === "MemberExpression" && left.property.type === "Identifier" && isFunctionNode(right)) {
                if (
                    (left.object.type === "Identifier" && left.object.name === "exports") ||
                    isModuleExportsMember(left.object)
                ) {
                    markFunctionName(left.property.name);
                }
            }
        }

        if (!hasInit) {
            return {
                title: "Missing function: init",
                details: "Define a function named init or assign a function to exports.init/module.exports.init.",
            };
        }

        if (!hasTick) {
            return {
                title: "Missing function: tick",
                details: "Define a function named tick or assign a function to exports.tick/module.exports.tick.",
            };
        }

        return undefined;
    }
}
