package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Service
public class BulkProjectService {

    private static final List<String> COLUMNS = List.of(
            "id",
            "event_id",
            "team",
            "track",
            "name",
            "tagline",
            "long_description",
            "thumbnail",
            "demo_video_url",
            "repository_url",
            "live_link",
            "tech_tags",
            "image_gallery",
            "custom_answers",
            "status",
            "created_by",
            "created_at",
            "updated_at",
            "submitted_at"
    );

    private final JsonMapper jsonMapper;
    private final ProjectStore projectStore;

    public BulkProjectService(
            JsonMapper jsonMapper,
            ProjectStore projectStore
    ) {
        this.jsonMapper = jsonMapper;
        this.projectStore = projectStore;
    }

    public String exportCsv() {
        StringBuilder csv = new StringBuilder();

        writeRow(csv, COLUMNS);

        for (JsonNode project :
                projectStore.readAll()) {

            List<String> row = new ArrayList<>();

            for (String column : COLUMNS) {
                JsonNode value =
                        project.path(column);

                if (value.isArray()
                        || value.isObject()) {
                    try {
                        row.add(
                                jsonMapper.writeValueAsString(
                                        value
                                )
                        );
                    } catch (Exception e) {
                        throw new IllegalStateException(
                                "Could not serialize " + column,
                                e
                        );
                    }
                } else {
                    row.add(value.asText(""));
                }
            }

            writeRow(csv, row);
        }

        return csv.toString();
    }

    public int importCsv(
            byte[] bytes
    ) {
        String content =
                new String(
                        bytes,
                        StandardCharsets.UTF_8
                );

        List<List<String>> rows =
                parseCsv(content);

        if (rows.isEmpty()) {
            throw new IllegalArgumentException(
                    "CSV is empty"
            );
        }

        if (!COLUMNS.equals(rows.get(0))) {
            throw new IllegalArgumentException(
                    "CSV header does not match the required project schema"
            );
        }

        ArrayNode imported =
                JsonNodeFactory.instance.arrayNode();

        if (rows.size() > 1001) {
            throw new IllegalArgumentException(
                    "CSV contains more than 1000 projects"
            );
        }

        for (int rowIndex = 1;
             rowIndex < rows.size();
             rowIndex++) {

            List<String> row =
                    rows.get(rowIndex);

            if (row.size() == 1
                    && row.get(0).isBlank()) {
                continue;
            }

            if (row.size() != COLUMNS.size()) {
                throw new IllegalArgumentException(
                        "Row " + (rowIndex + 1) +
                                " has " + row.size() +
                                " columns; expected " +
                                COLUMNS.size()
                );
            }

            ObjectNode project =
                    JsonNodeFactory.instance.objectNode();

            for (int i = 0;
                 i < COLUMNS.size();
                 i++) {

                String column =
                        COLUMNS.get(i);

                String value =
                        row.get(i);

                if ("tech_tags".equals(column)
                        || "image_gallery".equals(column)) {

                    project.set(
                            column,
                            parseArray(
                                    value,
                                    column,
                                    rowIndex + 1
                            )
                    );

                } else if ("custom_answers".equals(column)) {

                    project.set(
                            column,
                            parseObject(
                                    value,
                                    column,
                                    rowIndex + 1
                            )
                    );

                } else {
                    project.put(
                            column,
                            value
                    );
                }
            }

            imported.add(project);
        }

        return projectStore.bulkUpsert(
                imported
        );
    }

    private ArrayNode parseArray(
            String value,
            String column,
            int row
    ) {
        if (value == null || value.isBlank()) {
            return JsonNodeFactory.instance.arrayNode();
        }

        try {
            JsonNode node =
                    jsonMapper.readTree(value);

            if (!node.isArray()) {
                throw new IllegalArgumentException(
                        "Row " + row +
                                ": " + column +
                                " must contain a JSON array"
                );
            }

            return (ArrayNode) node;

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Row " + row +
                            ": invalid JSON in " + column,
                    e
            );
        }
    }

    private ObjectNode parseObject(
            String value,
            String column,
            int row
    ) {
        if (value == null || value.isBlank()) {
            return JsonNodeFactory.instance.objectNode();
        }

        try {
            JsonNode node =
                    jsonMapper.readTree(value);

            if (!node.isObject()) {
                throw new IllegalArgumentException(
                        "Row " + row +
                                ": " + column +
                                " must contain a JSON object"
                );
            }

            return (ObjectNode) node;

        } catch (IllegalArgumentException e) {
            throw e;

        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Row " + row +
                            ": invalid JSON in " + column,
                    e
            );
        }
    }

    private void writeRow(
            StringBuilder csv,
            List<String> values
    ) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                csv.append(',');
            }

            csv.append(
                    escape(values.get(i))
            );
        }

        csv.append('\n');
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }

        return "\"" +
                value
                        .replace("\"", "\"\"")
                        .replace("\r\n", "\n")
                        .replace("\r", "\n") +
                "\"";
    }

    private List<List<String>> parseCsv(
            String content
    ) {
        List<List<String>> rows =
                new ArrayList<>();

        List<String> row =
                new ArrayList<>();

        StringBuilder field =
                new StringBuilder();

        boolean quoted = false;

        for (int i = 0; i < content.length(); i++) {
            char c =
                    content.charAt(i);

            if (quoted) {
                if (c == '"') {
                    if (i + 1 < content.length()
                            && content.charAt(i + 1) == '"') {
                        field.append('"');
                        i++;
                    } else {
                        quoted = false;
                    }
                } else {
                    field.append(c);
                }

                continue;
            }

            if (c == '"'
                    && field.isEmpty()) {
                quoted = true;
                continue;
            }

            if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                continue;
            }

            if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                continue;
            }

            if (c == '\r') {
                if (i + 1 < content.length()
                        && content.charAt(i + 1) == '\n') {
                    i++;
                }

                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                continue;
            }

            field.append(c);
        }

        if (quoted) {
            throw new IllegalArgumentException(
                    "CSV contains an unterminated quoted field"
            );
        }

        if (!row.isEmpty()
                || field.length() > 0) {
            row.add(field.toString());
            rows.add(row);
        }

        return rows;
    }
}
