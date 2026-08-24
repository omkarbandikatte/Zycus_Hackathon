package com.stockpulse.chat;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public Map<String, String> chat(@Valid @RequestBody ChatRequest request) {
        return Map.of("reply", service.reply(request.message()));
    }

    public record ChatRequest(@NotBlank String message) { }
}
