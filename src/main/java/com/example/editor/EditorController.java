package com.example.editor;



import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Controller
public class EditorController {

    private String currentDocumentState = "";

    // FIXED: Instead of a number, we keep a specific list of Connected IDs
    // This prevents "negative users" bugs completely.
    private final Set<String> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @MessageMapping("/edit")
    @SendTo("/topic/document")
    public DocMessage sendEdit(DocMessage message) {
        this.currentDocumentState = message.getContent();
        return message;
    }

    @MessageMapping("/join")
    @SendTo("/topic/history")
    public DocMessage joinUser(DocMessage message) {
        // Force update the count for the new user
        broadcastUserCount();
        return new DocMessage(currentDocumentState, "Server");
    }

    // --- EVENT LISTENERS ---

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        // Get the unique ID of the person who connected
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Add them to our list
        if(sessionId != null) {
            activeSessions.add(sessionId);
        }

        broadcastUserCount();
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        // Remove them from the list
        if(sessionId != null) {
            activeSessions.remove(sessionId);
        }

        broadcastUserCount();
    }

    // Helper: Count the size of the list and send it
    private void broadcastUserCount() {
        // The size() can never be negative!
        int count = activeSessions.size();
        DocMessage message = new DocMessage(String.valueOf(count), "Server");
        messagingTemplate.convertAndSend("/topic/users", message);
    }
}

