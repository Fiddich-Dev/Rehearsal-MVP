package com.mvp.rehearsal.service;

import com.mvp.rehearsal.dto.Script;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ActiveScriptStore {

    private static final String ATTR = "rehearsal.activeScript";

    public void put(HttpSession session, Script script) {
        session.setAttribute(ATTR, script);
    }

    public Optional<Script> get(HttpSession session) {
        Object o = session.getAttribute(ATTR);
        return o instanceof Script s ? Optional.of(s) : Optional.empty();
    }

    public void clear(HttpSession session) {
        session.removeAttribute(ATTR);
    }
}
