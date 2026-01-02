package com.example.editor;

public class DocMessage {
    private String content;
    private String sender;
    private String docId;
    private String title;

    public DocMessage() {}

    public DocMessage(String content, String sender, String docId) {
        this.content = content;
        this.sender = sender;
        this.docId = docId;
    }


    public DocMessage(String content, String sender, String docId, String title) {
        this.content = content;
        this.sender = sender;
        this.docId = docId;
        this.title = title;
    }

    // --- Getters & Setters ---
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
}