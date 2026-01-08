export function wsconnect(broadcaster, { onFetch, onWsEvent }) {
    const socket = new SockJS("/ws");
    const stompClient = Stomp.over(socket);
    stompClient.connect({}, () => {
        stompClient.subscribe(`/topic/channel/${broadcaster}`, (payload) => onWsEvent(JSON.parse(payload.body)));
        fetch(`/api/channels/${broadcaster}/assets`)
            .then((r) => {
                if (!r.ok) {
                    throw new Error("Failed to load assets");
                }
                return r.json();
            })
            .then(onFetch)
            .catch((error) => {
                console.error(error);
                showToast("Unable to load overlay assets. Retrying may help.", "error")
            });
    });
}

