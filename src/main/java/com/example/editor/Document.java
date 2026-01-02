package com.example.editor;

import javax.persistence.*;

@Entity
@Table(name = "documents")
public class Document {
    @Id
    private String docId;

    private String title;

    @Lob
    private String content;

    private String owner;

    public Document() {}
    public Document(String docId, String title, String content, String owner) {
        this.docId = docId;
        this.title = title;
        this.content = content;
        this.owner = owner;
    }

    // Getters and Setters
    public String getDocId() { return docId; }
    public void setDocId(String docId) { this.docId = docId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getOwner() { return owner; }
    public void setOwner(String owner) { this.owner = owner; }
}