const channelNameInput = document.getElementById("channel-search");

function onOpenOverlayButtonClick(event) {
    event.preventDefault();
    const channelName = channelNameInput.value.trim().toLowerCase();
    if (channelName) {
        const overlayUrl = `/view/${channelName}/broadcast`;
        window.location.href = overlayUrl;
    }
}
