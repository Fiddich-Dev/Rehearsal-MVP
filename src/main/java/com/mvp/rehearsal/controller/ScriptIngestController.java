package com.mvp.rehearsal.controller;

import com.mvp.rehearsal.dto.Script;
import com.mvp.rehearsal.service.ActiveScriptStore;
import com.mvp.rehearsal.service.PdfExtractor;
import com.mvp.rehearsal.service.ScriptParserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Controller
public class ScriptIngestController {

    private final ScriptParserService parser;
    private final PdfExtractor pdfExtractor;
    private final ActiveScriptStore store;

    public ScriptIngestController(
            ScriptParserService parser,
            PdfExtractor pdfExtractor,
            ActiveScriptStore store
    ) {
        this.parser = parser;
        this.pdfExtractor = pdfExtractor;
        this.store = store;
    }

    @PostMapping("/script/text")
    public String submitText(@RequestParam("text") String text, HttpSession session) {
        if (text == null || text.isBlank()) {
            return "redirect:/";
        }
        Script script = parser.parse(text);
        store.put(session, script);
        return "redirect:/role-select";
    }

    @PostMapping("/script/upload")
    public String submitFile(@RequestParam("file") MultipartFile file, HttpSession session) {
        if (file == null || file.isEmpty()) {
            return "redirect:/";
        }
        String text = readFileAsText(file);
        Script script = parser.parse(text);
        store.put(session, script);
        return "redirect:/role-select";
    }

    private String readFileAsText(MultipartFile file) {
        String name = file.getOriginalFilename();
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        try {
            byte[] bytes = file.getBytes();
            if (lower.endsWith(".pdf")) {
                return pdfExtractor.extract(bytes);
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "could not read uploaded file: " + e.getMessage(), e);
        }
    }
}
