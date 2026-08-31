package com.salarytracker.controller;

import com.salarytracker.service.DataService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 数据接口，与旧版 Flask 路由一一对应
 */
@RestController
public class DataController {

    private final DataService dataService;

    public DataController(DataService dataService) {
        this.dataService = dataService;
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", true);
        return result;
    }

    @GetMapping("/api/data")
    public Map<String, Object> getData() {
        return dataService.readData();
    }

    @PutMapping("/api/data")
    public ResponseEntity<Map<String, Object>> putData(@RequestBody(required = false) Map<String, Object> payload) {
        try {
            DataService.validate(payload);
        } catch (IllegalArgumentException e) {
            return error(e.getMessage());
        }
        try {
            int n = dataService.writeData(payload);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("ok", true);
            result.put("records", n);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return error("写入失败: " + e.getMessage());
        }
    }

    private ResponseEntity<Map<String, Object>> error(String msg) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("error", msg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
    }
}
