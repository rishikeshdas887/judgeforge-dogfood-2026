package com.dogfood.backend.service;

import com.dogfood.backend.security.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

@Service
public class CertificateService {

    private final ProjectStore projectStore;
    private final TeamStore teamStore;
    private final EventStore eventStore;
    private final CertificateStore certificateStore;
    private final AuditStore auditStore;

    public CertificateService(
            ProjectStore projectStore,
            TeamStore teamStore,
            EventStore eventStore,
            CertificateStore certificateStore,
            AuditStore auditStore
    ) {
        this.projectStore = projectStore;
        this.teamStore = teamStore;
        this.eventStore = eventStore;
        this.certificateStore = certificateStore;
        this.auditStore = auditStore;
    }

    public List<JsonNode> issueForProject(
            String projectId,
            String actor,
            String requestId
    ) {
        ObjectNode project =
                projectStore.find(projectId);

        if (project == null) {
            throw new IllegalArgumentException(
                    "Unknown project: " + projectId
            );
        }

        if (!"SUBMITTED".equals(
                project.path("status").asText()
        )) {
            throw new IllegalArgumentException(
                    "Certificates require a submitted project"
            );
        }

        String teamId =
                project.path("team").asText("");

        if (teamId.isBlank()) {
            throw new IllegalArgumentException(
                    "Project has no team"
            );
        }

        JsonNode team = null;

        for (JsonNode candidate :
                teamStore.readTeams()) {

            if (teamId.equals(
                    candidate.path("id").asText()
            )) {
                team = candidate;
                break;
            }
        }

        if (team == null) {
            throw new IllegalArgumentException(
                    "Project references an unknown team"
            );
        }

        JsonNode event = eventStore.read();

        String eventId =
                project.path("event_id")
                        .asText(
                                event.path("id").asText("")
                        );

        String eventName =
                event.path("name").asText("");

        List<JsonNode> created =
                new ArrayList<>();

        for (JsonNode member :
                team.path("members")) {

            String participantId =
                    member.asText("").trim();

            if (participantId.isBlank()) {
                continue;
            }

            ObjectNode certificate =
                    certificateStore.issue(
                            projectId,
                            teamId,
                            team.path("name").asText(""),
                            participantId,
                            project.path("title")
                                    .asText(
                                            project.path(
                                                    "name"
                                            ).asText("")
                                    ),
                            eventId,
                            eventName,
                            project.path("track").asText("")
                    );

            created.add(certificate);

            if ("VALID".equals(
                    certificate.path("status").asText()
            )) {
                auditStore.append(
                        actor,
                        "CERTIFICATE_ISSUED",
                        "certificate:" +
                                certificate.path(
                                        "id"
                                ).asText(),
                        null,
                        certificate,
                        "certificate_issued",
                        requestId
                );
            }
        }

        return created;
    }

    public ObjectNode get(
            String certificateId
    ) {
        return certificateStore.find(
                certificateId
        );
    }

    public List<JsonNode> forParticipant(
            String participantId
    ) {
        return certificateStore.forParticipant(
                participantId
        );
    }

    public List<JsonNode> forProject(
            String projectId
    ) {
        return certificateStore.forProject(
                projectId
        );
    }
}
