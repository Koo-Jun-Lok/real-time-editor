package com.example.editor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
public class FileUploadController {


    private final Path fileStorageLocation;

    public FileUploadController() {

        this.fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @PostMapping("/upload")
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {

            String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
            String fileName = UUID.randomUUID().toString() + "_" + originalFileName;


            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);


            String fileDownloadUri = "/uploads/" + fileName;


            Map<String, String> response = new HashMap<>();
            response.put("url", fileDownloadUri);
            response.put("name", originalFileName);

            return ResponseEntity.ok(response);
        } catch (IOException ex) {
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Could not upload file"));
        }
    }
}