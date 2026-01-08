function detectPlatform() {
    const navigatorPlatform = (navigator.userAgentData?.platform || "").toLowerCase();
    const userAgent = (navigator.userAgent || "").toLowerCase();
    const platformString = `${navigatorPlatform} ${userAgent}`;

    if (platformString.includes("mac") || platformString.includes("darwin")) {
        return "mac";
    }
    if (platformString.includes("win")) {
        return "windows";
    }
    if (platformString.includes("linux")) {
        return "linux";
    }
    console.warn(`Unable to detect platform from string: ${platformString}`);
    return null;
}

function markRecommendedDownload(section) {
    const cards = Array.from(section.querySelectorAll(".download-card"));
    if (!cards.length) {
        return;
    }

    const platform = detectPlatform();
    const preferredCard = cards.find((card) => card.dataset.platform === platform) || cards[0];

    cards.forEach((card) => {
        const isPreferred = card === preferredCard;
        card.classList.toggle("download-card--active", isPreferred);
        const badge = card.querySelector(".recommended-badge");
        if (badge) {
            badge.classList.toggle("hidden", !isPreferred);
        }
    });
}

document.addEventListener("DOMContentLoaded", () => {
    const downloadSections = document.querySelectorAll(".download-section, .download-card-block");
    downloadSections.forEach(markRecommendedDownload);
});
