(function () {
    const CONTAINER_ID = 'toast-container';
    const DEFAULT_DURATION = 4200;

    function ensureContainer() {
        let container = document.getElementById(CONTAINER_ID);
        if (!container) {
            container = document.createElement('div');
            container.id = CONTAINER_ID;
            container.className = 'toast-container';
            container.setAttribute('aria-live', 'polite');
            container.setAttribute('aria-atomic', 'true');
            document.body.appendChild(container);
        }
        return container;
    }

    function buildToast(message, type) {
        const toast = document.createElement('div');
        toast.className = `toast toast-${type}`;

        const indicator = document.createElement('span');
        indicator.className = 'toast-indicator';
        indicator.setAttribute('aria-hidden', 'true');

        const content = document.createElement('div');
        content.className = 'toast-message';
        content.textContent = message;

        toast.appendChild(indicator);
        toast.appendChild(content);
        return toast;
    }

    function removeToast(toast) {
        if (!toast) return;
        toast.classList.add('toast-exit');
        setTimeout(() => toast.remove(), 250);
    }

    window.showToast = function showToast(message, type = 'info', options = {}) {
        if (!message) return;
        const normalized = ['success', 'error', 'warning', 'info'].includes(type) ? type : 'info';
        const duration = typeof options.duration === 'number' ? options.duration : DEFAULT_DURATION;
        const container = ensureContainer();
        const toast = buildToast(message, normalized);
        container.appendChild(toast);
        setTimeout(() => removeToast(toast), Math.max(1200, duration));
        toast.addEventListener('click', () => removeToast(toast));
    };
})();
