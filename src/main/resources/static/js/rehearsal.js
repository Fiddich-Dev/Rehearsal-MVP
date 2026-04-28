(() => {
    const root = document.querySelector('.rehearsal-screen');
    const userRole = root.dataset.userRole;
    const lineEls = Array.from(document.querySelectorAll('.lines li'));
    const lines = lineEls.map(el => ({
        index: Number(el.dataset.index),
        character: el.dataset.character,
        text: el.dataset.text,
        el
    }));
    const player = document.getElementById('player');
    const statusEl = document.getElementById('status-msg');
    const manualBtn = document.getElementById('manual-next');
    const cursorEl = document.getElementById('cursor');
    const doneScreen = document.getElementById('done-screen');

    let cursor = 0;
    let recognition = null;
    let advancing = false;

    function setStatus(text, cls) {
        statusEl.textContent = text;
        statusEl.className = 'status-msg' + (cls ? ' ' + cls : '');
    }

    function paintCursor() {
        lineEls.forEach((el, i) => {
            el.classList.remove('current');
            if (i < cursor) el.classList.add('done');
            else el.classList.remove('done');
        });
        if (cursor < lines.length) {
            const cur = lineEls[cursor];
            cur.classList.add('current');
            cur.scrollIntoView({ block: 'center', behavior: 'smooth' });
            cursorEl.textContent = String(cursor + 1);
        }
    }

    function stopRecognition() {
        if (!recognition) return;
        try { recognition.onend = null; recognition.stop(); } catch (_) {}
        recognition = null;
    }

    function advance() {
        if (advancing) return;
        advancing = true;
        stopRecognition();
        cursor += 1;
        if (cursor >= lines.length) {
            paintCursor();
            setStatus('완료', '');
            doneScreen.classList.remove('hidden');
            return;
        }
        paintCursor();
        advancing = false;
        runCurrent();
    }

    async function fetchTtsUrl(line) {
        const res = await fetch('/api/tts', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                text: line.text,
                character: line.character,
                line_index: line.index
            })
        });
        if (!res.ok) {
            const msg = await res.text().catch(() => '');
            throw new Error('tts http ' + res.status + ' ' + msg);
        }
        const data = await res.json();
        return data.audio_url;
    }

    async function playAi(line) {
        setStatus('TTS 로딩 중…', 'playing');
        try {
            const url = await fetchTtsUrl(line);
            player.src = url;
            setStatus(`▶ ${line.character} 재생 중`, 'playing');
            await player.play();
            await new Promise((resolve) => {
                player.onended = resolve;
                player.onerror = resolve;
            });
            advance();
        } catch (e) {
            console.error(e);
            const reason = (e && e.message) ? String(e.message).slice(0, 120) : '알 수 없는 오류';
            setStatus('TTS 실패: ' + reason + ' — 직접 넘기기로 진행', 'error');
        }
    }

    function startUserListen() {
        setStatus('🎤 듣고 있어요…', 'listening');
        const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
        if (!SR) {
            setStatus('내 차례 — [직접 넘기기]를 누르세요', 'listening');
            return;
        }
        try {
            recognition = new SR();
            recognition.lang = 'ko-KR';
            recognition.interimResults = false;
            recognition.continuous = false;
            recognition.onend = () => {
                if (!advancing) advance();
            };
            recognition.onerror = (ev) => {
                console.warn('speech recognition error', ev.error);
                if (ev.error === 'not-allowed' || ev.error === 'service-not-allowed') {
                    setStatus('마이크 권한 없음 — [직접 넘기기]로 진행', 'error');
                    stopRecognition();
                }
            };
            recognition.start();
        } catch (e) {
            console.warn('cannot start recognition', e);
            setStatus('내 차례 — [직접 넘기기]를 누르세요', 'listening');
        }
    }

    function runCurrent() {
        if (cursor >= lines.length) return;
        const line = lines[cursor];
        if (line.character === userRole) {
            startUserListen();
        } else {
            playAi(line);
        }
    }

    manualBtn.addEventListener('click', () => advance());

    document.addEventListener('keydown', (e) => {
        if (e.code !== 'Space' && e.key !== ' ') return;
        const tag = (document.activeElement && document.activeElement.tagName || '').toUpperCase();
        if (tag === 'INPUT' || tag === 'TEXTAREA') return;
        e.preventDefault();
        advance();
    });

    paintCursor();
    runCurrent();
})();
