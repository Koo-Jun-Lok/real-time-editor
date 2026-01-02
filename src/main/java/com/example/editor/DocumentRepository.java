package com.example.editor;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;


interface DocumentRepository extends JpaRepository<Document, String> {
    List<Document> findByOwner(String owner);
    List<Document> findAll();
}