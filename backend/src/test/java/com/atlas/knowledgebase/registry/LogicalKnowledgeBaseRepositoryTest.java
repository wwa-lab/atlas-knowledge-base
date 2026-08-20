package com.atlas.knowledgebase.registry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.atlas.knowledgebase.session.AtlasUserRecord;
import com.atlas.knowledgebase.session.AtlasUserRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Import(LogicalKnowledgeBaseRepositoryTest.ConflictProbeConfiguration.class)
class LogicalKnowledgeBaseRepositoryTest {

    @Autowired private AtlasUserRepository atlasUserRepository;
    @Autowired private LogicalKnowledgeBaseRepository knowledgeBaseRepository;
    @Autowired private BindingRepository bindingRepository;
    @Autowired private MockMvc mockMvc;

    @Test
    void draftUpdateIncrementsConfigVersion() {
        SeededKb seeded = seedDraft("lkb_007_ok");

        LogicalKnowledgeBaseRecord updated =
                knowledgeBaseRepository.updateDraft(
                        seeded.kb().logicalKbId(), seeded.kb().configVersion(), rename(seeded, "Renamed"));

        assertThat(updated.configVersion()).isEqualTo(2);
        assertThat(updated.name()).isEqualTo("Renamed");
        assertThat(updated.lifecycle()).isEqualTo("draft");
    }

    @Test
    void staleDraftUpdateThrowsConfigVersionConflict() {
        SeededKb seeded = seedDraft("lkb_007_stale");
        knowledgeBaseRepository.updateDraft(
                seeded.kb().logicalKbId(), seeded.kb().configVersion(), rename(seeded, "First"));

        assertThatThrownBy(
                        () ->
                                knowledgeBaseRepository.updateDraft(
                                        seeded.kb().logicalKbId(),
                                        seeded.kb().configVersion(),
                                        rename(seeded, "Second")))
                .isInstanceOf(ConfigVersionConflictException.class)
                .satisfies(
                        ex -> {
                            ConfigVersionConflictException conflict = (ConfigVersionConflictException) ex;
                            assertThat(conflict.expectedVersion()).isEqualTo(1);
                            assertThat(conflict.actualVersion()).isEqualTo(2);
                        });
    }

    @Test
    void activateBumpsVersionAndSetsActive() {
        SeededKb seeded = seedDraft("lkb_007_act");

        LogicalKnowledgeBaseRecord activated =
                knowledgeBaseRepository.activate(seeded.kb().logicalKbId(), seeded.kb().configVersion());

        assertThat(activated.lifecycle()).isEqualTo("active");
        assertThat(activated.configVersion()).isEqualTo(2);
        assertThat(activated.activatedAt()).isNotNull();
    }

    @Test
    void staleActivateThrowsConfigVersionConflict() {
        SeededKb seeded = seedDraft("lkb_007_act_stale");
        knowledgeBaseRepository.updateDraft(
                seeded.kb().logicalKbId(), seeded.kb().configVersion(), rename(seeded, "Edited"));

        assertThatThrownBy(
                        () ->
                                knowledgeBaseRepository.activate(
                                        seeded.kb().logicalKbId(), seeded.kb().configVersion()))
                .isInstanceOf(ConfigVersionConflictException.class);
    }

    @Test
    void activateRejectsNonDraftWithoutTreatingAsVersionConflict() {
        SeededKb seeded = seedDraft("lkb_007_not_draft");
        LogicalKnowledgeBaseRecord active =
                knowledgeBaseRepository.activate(seeded.kb().logicalKbId(), seeded.kb().configVersion());

        assertThatThrownBy(
                        () ->
                                knowledgeBaseRepository.activate(
                                        active.logicalKbId(), active.configVersion()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("active");
    }

    @Test
    void bindingUpdateConflictsOnStaleConfigVersion() {
        SeededKb seeded = seedDraft("lkb_007_bind");
        BindingRecord binding =
                bindingRepository.insert(
                        new BindingRecord(
                                "bnd_007_1",
                                seeded.kb().logicalKbId(),
                                "git_markdown",
                                "{\"repo\":\"org/repo\"}",
                                "canonical",
                                "delegated_user",
                                "healthy",
                                true,
                                false,
                                false,
                                null,
                                "{}",
                                "owner@example.com",
                                null,
                                1,
                                Instant.parse("2026-08-20T06:00:00Z"),
                                Instant.parse("2026-08-20T06:00:00Z")));

        BindingDraft draft =
                new BindingDraft(
                        binding.providerProfile(),
                        binding.sourceIdentityJson(),
                        binding.bindingRole(),
                        binding.authMethod(),
                        binding.health(),
                        binding.enabled(),
                        binding.killSwitch(),
                        binding.featureFlag(),
                        binding.freshnessPolicyJson(),
                        "{\"roots\":[\"docs\"]}",
                        binding.credentialOwner(),
                        binding.regionConstraintsJson());

        BindingRecord updated = bindingRepository.update(binding.bindingId(), 1, draft);
        assertThat(updated.configVersion()).isEqualTo(2);

        assertThatThrownBy(() -> bindingRepository.update(binding.bindingId(), 1, draft))
                .isInstanceOf(ConfigVersionConflictException.class);
    }

    @Test
    void configVersionConflictMapsToHttp409Envelope() throws Exception {
        mockMvc.perform(
                        post("/__probe/config-version-conflict").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.category").value("conflict"))
                .andExpect(jsonPath("$.error.code").value("CONFIG_VERSION_CONFLICT"))
                .andExpect(jsonPath("$.error.details.expected_config_version").value(1))
                .andExpect(jsonPath("$.error.details.actual_config_version").value(2));
    }

    private SeededKb seedDraft(String logicalKbId) {
        Instant now = Instant.parse("2026-08-20T06:00:00Z");
        String userId = "usr_" + logicalKbId;
        atlasUserRepository.insert(
                new AtlasUserRecord(
                        userId, "sso-" + logicalKbId, "Owner", null, "[\"kb_owner\"]", false, now, now));
        LogicalKnowledgeBaseRecord kb =
                knowledgeBaseRepository.insert(
                        new LogicalKnowledgeBaseRecord(
                                logicalKbId,
                                "Draft KB",
                                "desc",
                                userId,
                                "private",
                                "support",
                                "internal",
                                false,
                                "browse_only",
                                "draft",
                                "healthy",
                                1,
                                null,
                                false,
                                null,
                                now,
                                now,
                                null));
        return new SeededKb(kb);
    }

    private static LogicalKnowledgeBaseDraft rename(SeededKb seeded, String name) {
        LogicalKnowledgeBaseRecord kb = seeded.kb();
        return new LogicalKnowledgeBaseDraft(
                name,
                kb.description(),
                kb.ownerUserId(),
                kb.discoverability(),
                kb.purpose(),
                kb.classification(),
                kb.modelEligible(),
                kb.capability(),
                kb.health(),
                kb.maxStaleness(),
                kb.freshnessRequired(),
                kb.accessRequestUrl());
    }

    private record SeededKb(LogicalKnowledgeBaseRecord kb) {}

    @TestConfiguration
    static class ConflictProbeConfiguration {
        @RestController
        static class ConflictProbeController {
            @PostMapping("/__probe/config-version-conflict")
            void conflict() {
                throw new ConfigVersionConflictException("logical_knowledge_base", "lkb_probe", 1, 2);
            }
        }
    }
}
