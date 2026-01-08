const assetModal = document.getElementById("custom-asset-modal");
const userSourceTextArea = document.getElementById("custom-asset-code");
const formErrorWrapper = document.getElementById("custom-asset-error");
const jsErrorTitle = document.getElementById("js-error-title");
const jsErrorDetails = document.getElementById("js-error-details");

function toggleCustomAssetModal(event) {
    if (event !== undefined && event.target !== event.currentTarget) {
        return;
    }
    if (assetModal.classList.contains("hidden")) {
        assetModal.classList.remove("hidden");
    } else {
        assetModal.classList.add("hidden");
    }
}

function submitCodeAsset(formEvent) {
    formEvent.preventDefault();
    const src = userSourceTextArea.value;
    const error = getUserJavaScriptSourceError(src);
    if (error) {
        jsErrorTitle.textContent = error.title;
        jsErrorDetails.textContent = error.details;
        formErrorWrapper.classList.remove("hidden");
        return false;
    }
    formErrorWrapper.classList.add("hidden");
    jsErrorTitle.textContent = "";
    jsErrorDetails.textContent = "";
    return false;
}

function getUserJavaScriptSourceError(src) {
    let ast;

    try {
        ast = acorn.parse(src, {
            ecmaVersion: "latest",
            sourceType: "script",
        });
    } catch (e) {
        return { title: "Syntax Error", details: e.message };
    }

    const functionNames = ast.body.filter((node) => node.type === "FunctionDeclaration").map((node) => node.id.name);
    if (!functionNames.includes("init")) {
        return { title: "Missing function: init", details: "Your code must include a function named 'init'." };
    }
    if (!functionNames.includes("tick")) {
        return { title: "Missing function: tick", details: "Your code must include a function named 'tick'." };
    }

    return undefined;
}
