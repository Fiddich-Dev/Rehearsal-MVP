(() => {
    const overlay = document.getElementById('loading-overlay');
    const forms = document.querySelectorAll('form.ingest-form');
    if (!overlay || forms.length === 0) return;

    function showOverlay() {
        overlay.classList.remove('hidden');
        overlay.setAttribute('aria-hidden', 'false');
    }

    forms.forEach((form) => {
        form.addEventListener('submit', () => {
            const btn = form.querySelector('button[type="submit"]');
            if (btn) {
                btn.disabled = true;
                btn.textContent = '분석 중…';
            }
            forms.forEach((f) => {
                f.querySelectorAll('button[type="submit"]').forEach((b) => { b.disabled = true; });
            });
            showOverlay();
        });
    });

    window.addEventListener('pageshow', (e) => {
        if (e.persisted) {
            overlay.classList.add('hidden');
            overlay.setAttribute('aria-hidden', 'true');
            forms.forEach((f) => {
                f.querySelectorAll('button[type="submit"]').forEach((b) => {
                    b.disabled = false;
                });
            });
            const textBtn = document.querySelector('form.text-form button[type="submit"]');
            if (textBtn) textBtn.textContent = '대본으로 시작';
            const fileBtn = document.querySelector('form.upload-form button[type="submit"]');
            if (fileBtn) fileBtn.textContent = '업로드 후 시작';
        }
    });
})();
