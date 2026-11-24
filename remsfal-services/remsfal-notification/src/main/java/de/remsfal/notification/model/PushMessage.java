package de.remsfal.notification.model;

import java.util.Map;
import java.util.UUID;

public record PushMessage(String type, UUID issueId, String body, int ttlSeconds) {

    public static PushMessage test() {
        return new PushMessage("test", null, "Das ist Ihre Testbenachrichtigung.", 60);
    }

    public static PushMessage issueStatusChanged(final UUID issueId) {
        return new PushMessage("issue-status-changed", issueId, "Der Status Ihrer Meldung wurde aktualisiert.", 3600);
    }

    public String title() {
        return "REMSFAL";
    }

    public Map<String, String> data() {
        return issueId == null ? Map.of("type", type) : Map.of("type", type, "issueId", issueId.toString());
    }
}
