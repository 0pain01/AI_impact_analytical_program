package com.aiimpacteval.apicore.admin;

import com.aiimpacteval.apicore.admin.AdminConnectorService.ConnectorHealth;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin & Access Console read APIs (PRD E8). ADMIN-only — enforced in SecurityConfig. */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminController {

    private final AdminConnectorService connectorService;

    public AdminController(AdminConnectorService connectorService) {
        this.connectorService = connectorService;
    }

    @GetMapping("/connectors")
    public List<ConnectorHealth> connectors() {
        return connectorService.listConnectors();
    }
}
