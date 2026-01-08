async function spawnUserJavaScriptWorker(asset) {
    let assetSource;
    try {
        assetSource = await fetch(asset.url).then((r) => r.text());
    } catch (error) {
        console.error(`Unable to fetch asset with id:${id} from url:${asset.url}`, error);
        return;
    }
    const blob = new Blob([assetSource], { type: "application/javascript" });
    const worker = new Worker(URL.createObjectURL(blob));
    worker.onmessage = (event) => {
        console.log("Message from worker:", event.data);
    };
    worker.postMessage(data);
}
