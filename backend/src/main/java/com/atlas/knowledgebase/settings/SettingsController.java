package com.atlas.knowledgebase.settings;

import com.atlas.knowledgebase.providers.ProviderConnectionService;
import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.CurrentRequestAuth;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final ProviderConnectionService connections;

    public SettingsController(ProviderConnectionService connections) {
        this.connections = connections;
    }

    @GetMapping
    public Map<String, Object> getSettings(HttpServletRequest request) {
        AtlasUserRecord user = CurrentRequestAuth.requireUser(request);
        return connections.settingsProjection(user);
    }
}
