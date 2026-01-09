export function createCustomAssetModal({ broadcaster, showToast = globalThis.showToast, onAssetSaved }) {
    const assetModal = document.getElementById("custom-asset-modal");
    const userNameInput = document.getElementById("custom-asset-name");
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
    let attachmentState = [];

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

    const openModal = () => {
        assetModal?.classList.remove("hidden");
    };

    const closeModal = () => {
        assetModal?.classList.add("hidden");
    };

    const openNew = () => {
        if (userNameInput) {
            userNameInput.value = "";
        }
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
        const submitButton = formEvent.currentTarget?.querySelector('button[type="submit"]');
        if (submitButton) {
            submitButton.disabled = true;
            submitButton.textContent = "Saving...";
        }
        saveCodeAsset({ name, src, assetId })
            .then((asset) => {
                if (asset) {
                    onAssetSaved?.(asset);
                }
                closeModal();
                showToast?.(assetId ? "Custom asset updated." : "Custom asset created.", "success");
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

    return { openNew, openEditor };

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

    function saveCodeAsset({ name, src, assetId }) {
        const payload = { name, source: src };
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
