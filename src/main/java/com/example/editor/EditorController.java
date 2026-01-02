package com.example.editor;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

@RestController
public class EditorController {

    @Autowired private UserRepository userRepository;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private SimpMessagingTemplate messagingTemplate;

    private final BlockingQueue<DocMessage> editQueue = new LinkedBlockingQueue<>();
    private final Map<String, String> documentStates = new ConcurrentHashMap<>();
    private final Set<String> dirtyDocIds = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private final Set<String> activeSessions = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // Requirement f: Implement Lock interface
    private final Lock globalLock = new ReentrantLock();

    // Requirement a: Thread objects
    private Thread workerThread;
    private Thread backupThread;
    private volatile boolean isRunning = true;

    // --- API & Listeners  ---
    @PostMapping("/api/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        Optional<User> userOpt = userRepository.findById(username);
        if (userOpt.isPresent()) {
            if (!userOpt.get().getPassword().equals(password)) return ResponseEntity.status(401).body("Wrong password");
        } else {
            userRepository.save(new User(username, password));
        }
        return ResponseEntity.ok(Collections.singletonMap("status", "ok"));
    }

    @GetMapping("/api/my-docs")
    public List<Document> getMyDocs(@RequestParam(required = false) String username) {
        return documentRepository.findAll();
    }

    @PostMapping("/api/create-doc")
    public ResponseEntity<?> createDoc(@RequestBody Map<String, String> body) {
        String docId = UUID.randomUUID().toString();
        String title = body.get("title");
        String owner = body.get("owner");
        Document newDoc = new Document(docId, title, "", owner);
        documentRepository.saveAndFlush(newDoc);
        return ResponseEntity.ok(Collections.singletonMap("docId", docId));
    }

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            activeSessions.add(sessionId);
            broadcastUserCount();
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        if (sessionId != null) {
            activeSessions.remove(sessionId);
            broadcastUserCount();
        }
    }

    private void broadcastUserCount() {
        int count = activeSessions.size();
        DocMessage message = new DocMessage(String.valueOf(count), "System", "GLOBAL");
        messagingTemplate.convertAndSend("/topic/users", message);
    }

    // --- Concurrent Logic ---

    @PostConstruct
    public void init() {
        // Requirement a: Runnable (Lambda)
        workerThread = new Thread(() -> {
            while (isRunning) {
                try { processEdit(editQueue.take()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        workerThread.setName("Editor-Worker-Thread");
        workerThread.start();

        backupThread = new Thread(() -> {
            while (isRunning) {
                try {
                    // Requirement b: Thread influencing (sleep)
                    Thread.sleep(2000);
                    if (!dirtyDocIds.isEmpty()) {
                        performBatchBackupToDB();
                    }
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
        });
        backupThread.setName("Editor-Backup-Thread");
        backupThread.start();
    }

    @MessageMapping("/join/{docId}")
    public void joinUser(@DestinationVariable String docId, DocMessage message, StompHeaderAccessor header) {
        // Requirement d: Liveness - Use tryLock to avoid deadlock/starvation
        boolean locked = false;
        try {
            locked = globalLock.tryLock(5, TimeUnit.SECONDS); // Wait max 5 seconds
            if (locked) {
                // Requirement e: Synchronizing access (Critical Section)
                String content = documentStates.get(docId);
                String docTitle = "Untitled Document";

                if (content == null) {
                    Optional<Document> docOpt = documentRepository.findById(docId);
                    if (docOpt.isPresent()) {
                        content = docOpt.get().getContent();
                        docTitle = docOpt.get().getTitle();
                        System.out.println("✅ Loaded from DB: " + docTitle);
                    } else {
                        content = "";
                        System.out.println("⚠️ New/Empty Doc: " + docId);
                    }
                    documentStates.put(docId, content);
                } else {
                    Optional<Document> docOpt = documentRepository.findById(docId);
                    if (docOpt.isPresent()) docTitle = docOpt.get().getTitle();
                }

                DocMessage response = new DocMessage(content, "Server", docId, docTitle);
                messagingTemplate.convertAndSend("/topic/history/" + message.getSender(), response);
            } else {
                System.err.println("Could not acquire lock for joinUser - System is busy");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) globalLock.unlock();
        }
    }

    @MessageMapping("/users")
    public void requestUserCount() { broadcastUserCount(); }

    @MessageMapping("/edit/{docId}")
    public void receiveEdit(@DestinationVariable String docId, DocMessage message) {
        message.setDocId(docId);
        try { editQueue.put(message); } catch (InterruptedException e) {}
    }

    private void processEdit(DocMessage message) {
        String docId = message.getDocId();
        if (docId == null) return;

        boolean locked = false;
        try {
            // Requirement d: Liveness (tryLock)
            locked = globalLock.tryLock(5, TimeUnit.SECONDS);
            if (locked) {
                // Requirement e: Synchronizing access
                documentStates.put(docId, message.getContent());
                dirtyDocIds.add(docId);
                messagingTemplate.convertAndSend("/topic/document/" + docId, message);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (locked) globalLock.unlock();
        }
    }

    private void performBatchBackupToDB() {
        // Make a copy to process safely
        Set<String> docsToSave = new HashSet<>(dirtyDocIds);
        dirtyDocIds.removeAll(docsToSave);

        // Requirement g: Parallel Streams (reduction/processing)
        // 使用 parallelStream 并行处理保存任务，提高性能
        docsToSave.parallelStream().forEach(docId -> {
            String content = documentStates.get(docId);
            if (content != null) {
                // JPA Repository is typically thread-safe for individual operations
                Optional<Document> docOpt = documentRepository.findById(docId);
                if (docOpt.isPresent()) {
                    Document doc = docOpt.get();
                    doc.setContent(content);
                    documentRepository.saveAndFlush(doc);
                    System.out.println("💾 [Thread-" + Thread.currentThread().getId() + "] Saved Update: " + doc.getTitle());
                } else {
                    Document newDoc = new Document(docId, "Auto-Saved Doc", content, "Anonymous");
                    documentRepository.saveAndFlush(newDoc);
                    System.out.println("💾 [Thread-" + Thread.currentThread().getId() + "] Saved New: " + docId);
                }
            }
        });
    }

    @PreDestroy
    public void cleanup() {
        isRunning = false;
        System.out.println("🛑 Stopping server...");

        // Interrupt threads
        if (workerThread != null) workerThread.interrupt();
        if (backupThread != null) backupThread.interrupt();

        // Requirement c: Implement joining threads
        try {
            if (workerThread != null) workerThread.join(1000); // Wait 1s for it to finish
            if (backupThread != null) backupThread.join(1000);
            System.out.println("✅ Threads joined successfully.");
        } catch (InterruptedException e) {
            System.err.println("Threads failed to join.");
        }

        performBatchBackupToDB(); // Final save
    }
}