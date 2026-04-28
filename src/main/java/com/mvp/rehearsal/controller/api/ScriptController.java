package com.mvp.rehearsal.controller.api;

import com.mvp.rehearsal.dto.Script;
import com.mvp.rehearsal.service.ScriptService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class ScriptController {

    private final ScriptService scriptService;

    public ScriptController(ScriptService scriptService) {
        this.scriptService = scriptService;
    }

    @GetMapping("/script")
    public Script script() {
        return scriptService.getScript();
    }
}
