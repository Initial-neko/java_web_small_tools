package com.toolbox.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toolbox.core.ToolResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;

/** Explicit task navigation and HTTP contracts; Java helper classes do not create pages. */
@RestController
public class ToolHelpController {
    private final JsonNode catalog;

    public ToolHelpController(ObjectMapper mapper) throws IOException {
        try (InputStream input = new ClassPathResource("tool-help.json").getInputStream()) {
            catalog = mapper.readTree(input);
        }
    }

    @GetMapping("/api/help")
    public JsonNode catalog() {
        return catalog;
    }

    @GetMapping("/api/tools/{name}/help")
    public ResponseEntity<?> tool(@PathVariable String name) {
        for (JsonNode tool : catalog.path("tools")) {
            if (name.equals(tool.path("name").asText())) return ResponseEntity.ok(tool);
        }
        return ResponseEntity.status(404).body(ToolResult.fail("工具帮助不存在: " + name));
    }
}
