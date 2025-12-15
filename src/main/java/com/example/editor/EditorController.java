package com.example.editor;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class EditorController {

    @MessageMapping("/edit") // Listens for messages sent to /app/edit
    @SendTo("/topic/document") // Broadcasts the return value to everyone subscribed to /topic/document
    public DocMessage sendEdit(DocMessage message) {
        return message; // Simply return the message to broadcast it
    }
}