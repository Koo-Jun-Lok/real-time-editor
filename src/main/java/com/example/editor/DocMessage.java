package com.example.editor;



public class DocMessage {
    private Object payload; // Can hold the complex "Delta" object (insert/delete instructions)
    private String sender;

    public DocMessage() {}

    public DocMessage(Object payload, String sender) {
        this.payload = payload;
        this.sender = sender;
    }

    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
}