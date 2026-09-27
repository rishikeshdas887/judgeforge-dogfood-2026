package com.dogfood.backend.webhook;

import tools.jackson.databind.JsonNode;

public record AuditEventCreated(JsonNode event) {
}
