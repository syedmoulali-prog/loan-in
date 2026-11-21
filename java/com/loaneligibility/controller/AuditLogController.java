package com.loaneligibility.controller;

import com.loaneligibility.dto.AuditLogDto;
import com.loaneligibility.service.AuditLogService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit-logs")
@PreAuthorize("hasRole('ADMINISTRATOR')")
public class AuditLogController {

    private final AuditLogService auditLogService;

    public AuditLogController(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @GetMapping
    public ResponseEntity<List<AuditLogDto>> getAllAuditLogs() {
        return ResponseEntity.ok(auditLogService.getAllLogs());
    }

    @GetMapping("/entity/{entityType}")
    public ResponseEntity<List<AuditLogDto>> getAuditLogsByEntity(@PathVariable String entityType) {
        return ResponseEntity.ok(auditLogService.getLogsByEntity(entityType));
    }
}
