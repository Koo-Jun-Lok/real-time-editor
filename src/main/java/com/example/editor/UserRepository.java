package com.example.editor;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// 操作 User 表
public interface UserRepository extends JpaRepository<User, String> {
}
