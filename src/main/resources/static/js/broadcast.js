import { BroadcastRenderer } from "./broadcast/renderer.js";

const canvas = document.getElementById("broadcast-canvas");
const scriptLayer = document.getElementById("broadcast-script-layer");
const renderer = new BroadcastRenderer({ canvas, scriptLayer, broadcaster, showToast });

renderer.start();
