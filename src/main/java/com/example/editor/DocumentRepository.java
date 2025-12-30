package com.example.editor;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

// 操作 Document 表
interface DocumentRepository extends JpaRepository<Document, String> {
    // 自动生成 SQL: SELECT * FROM documents WHERE owner = ?
    List<Document> findByOwner(String owner);
}