package com.example.editor;

import javax.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    private String username; // 用户名作为主键
    private String password; // 简单起见，暂存明文

    public User() {}
    public User(String username, String password) {
        this.username = username;
        this.password = password;
    }
    // Getters Setters
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}