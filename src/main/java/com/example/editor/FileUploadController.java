package com.example.editor;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@Controller
public class FileUploadController {

    // 图片会存在项目根目录下的 "uploads" 文件夹里
    private final Path fileStorageLocation = Paths.get("uploads").toAbsolutePath().normalize();

    public FileUploadController() {
        try {
            // 如果文件夹不存在，自动创建它
            Files.createDirectories(this.fileStorageLocation);
        } catch (Exception ex) {
            throw new RuntimeException("Could not create the directory where the uploaded files will be stored.", ex);
        }
    }

    @PostMapping(value = "/upload", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<Map<String, String>> uploadFile(@RequestParam("file") MultipartFile file) {
        try {
            // 1. 生成一个唯一的文件名 (防止两张图叫同一个名字)
            String originalFileName = StringUtils.cleanPath(file.getOriginalFilename());
            String fileName = UUID.randomUUID().toString() + "_" + originalFileName.replaceAll("\\s+", "_");

            // 2. 把文件保存到硬盘
            Path targetLocation = this.fileStorageLocation.resolve(fileName);
            Files.copy(file.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);

            // 3. 告诉前端图片的访问地址
            Map<String, String> response = new HashMap<>();
            // 这个 URL 对应 WebConfig 里的配置
            response.put("url", "/uploads/" + fileName);
            response.put("name", fileName);

            return ResponseEntity.ok(response);

        } catch (IOException ex) {
            ex.printStackTrace();
            return ResponseEntity.status(500).body(Collections.singletonMap("error", "Upload failed"));
        }
    }
}