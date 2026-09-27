package com.dogfood.backend.service;

import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;

@Service
public class JudgeRecordSigningService {

    private static final String ALGORITHM = "Ed25519";

    private final JsonMapper jsonMapper;
    private final Path keyPath;

    private final PrivateKey privateKey;
    private final PublicKey publicKey;
    private final String keyId;
    private final String publicKeyBase64;

    public JudgeRecordSigningService(
            JsonMapper jsonMapper
    ) {
        this.jsonMapper = jsonMapper;
        this.keyPath =
                Path.of("data/judge-record-signing.json");

        try {
            Files.createDirectories(
                    keyPath.getParent()
            );

            KeyMaterial material =
                    loadOrCreate();

            this.privateKey =
                    material.privateKey();
            this.publicKey =
                    material.publicKey();
            this.keyId =
                    material.keyId();
            this.publicKeyBase64 =
                    material.publicKeyBase64();

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not initialize judge record signing",
                    e
            );
        }
    }

    public ObjectNode sign(ObjectNode payload) {
        try {
            ObjectNode canonicalPayload =
                    payloadFromSignedRecord(payload);

            String canonical =
                    canonical(canonicalPayload);

            byte[] hash =
                    sha256(
                            canonical.getBytes(
                                    StandardCharsets.UTF_8
                            )
                    );

            Signature signature =
                    Signature.getInstance(ALGORITHM);

            signature.initSign(privateKey);
            signature.update(
                    canonical.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            byte[] signed =
                    signature.sign();

            ObjectNode record =
                    payload.deepCopy();

            record.put(
                    "record_hash_sha256",
                    hex(hash)
            );
            record.put(
                    "signature_algorithm",
                    ALGORITHM
            );
            record.put(
                    "key_id",
                    keyId
            );
            record.put(
                    "signature",
                    Base64.getEncoder()
                            .encodeToString(signed)
            );
            record.put(
                    "signed_at",
                    Instant.now().toString()
            );

            return record;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "Could not sign judge record",
                    e
            );
        }
    }

    public boolean verify(ObjectNode record) {
        try {
            String signatureText =
                    record.path("signature").asText("");

            String recordHash =
                    record.path(
                            "record_hash_sha256"
                    ).asText("");

            if (signatureText.isBlank()
                    || recordHash.isBlank()) {
                return false;
            }

            ObjectNode payload =
                    payloadFromSignedRecord(record);

            String canonical =
                    canonical(payload);

            String calculatedHash =
                    hex(
                            sha256(
                                    canonical.getBytes(
                                            StandardCharsets.UTF_8
                                    )
                            )
                    );

            if (!MessageDigest.isEqual(
                    calculatedHash.getBytes(
                            StandardCharsets.UTF_8
                    ),
                    recordHash.getBytes(
                            StandardCharsets.UTF_8
                    )
            )) {
                return false;
            }

            Signature verifier =
                    Signature.getInstance(ALGORITHM);

            verifier.initVerify(publicKey);
            verifier.update(
                    canonical.getBytes(
                            StandardCharsets.UTF_8
                    )
            );

            return verifier.verify(
                    Base64.getDecoder().decode(
                            signatureText
                    )
            );

        } catch (Exception e) {
            return false;
        }
    }

    public ObjectNode verification(
            ObjectNode record
    ) {
        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        result.put(
                "record_id",
                record.path("id").asText("")
        );
        result.put(
                "valid",
                verify(record)
        );
        result.put(
                "algorithm",
                ALGORITHM
        );
        result.put(
                "key_id",
                keyId
        );

        return result;
    }

    public ObjectNode publicKey() {
        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        result.put("key_id", keyId);
        result.put(
                "algorithm",
                ALGORITHM
        );
        result.put(
                "public_key_format",
                "X.509 SubjectPublicKeyInfo"
        );
        result.put(
                "public_key_base64",
                publicKeyBase64
        );

        return result;
    }

    private ObjectNode payloadFromSignedRecord(
            ObjectNode record
    ) {
        ObjectNode payload =
                JsonNodeFactory.instance.objectNode();

        payload.put(
                "id",
                record.path("id").asText("")
        );
        payload.put(
                "type",
                record.path("type").asText("")
        );
        payload.put(
                "judge",
                record.path("judge").asText("")
        );
        payload.put(
                "project",
                record.path("project").asText("")
        );
        payload.set(
                "criteria",
                sortedObject(
                        record.path("criteria")
                )
        );
        payload.put(
                "comment",
                record.path("comment").asText("")
        );
        payload.put(
                "weighted_score",
                record.path(
                        "weighted_score"
                ).asDouble()
        );
        payload.put(
                "issued_at",
                record.path("issued_at").asText("")
        );

        return payload;
    }

    private String canonical(
            ObjectNode payload
    ) throws IOException {
        return jsonMapper.writeValueAsString(
                payload
        );
    }

    private ObjectNode sortedObject(
            JsonNode source
    ) {
        ObjectNode result =
                JsonNodeFactory.instance.objectNode();

        if (source == null || !source.isObject()) {
            return result;
        }

        ObjectNode object =
                (ObjectNode) source;

        java.util.List<String> names =
                new java.util.ArrayList<>();

        object.properties().forEach(
                entry -> names.add(entry.getKey())
        );

        names.sort(String::compareTo);

        for (String name : names) {
            result.set(
                    name,
                    object.path(name).deepCopy()
            );
        }

        return result;
    }

    private KeyMaterial loadOrCreate()
            throws Exception {

        if (!Files.exists(keyPath)) {
            KeyPairGenerator generator =
                    KeyPairGenerator.getInstance(
                            ALGORITHM
                    );

            KeyPair pair =
                    generator.generateKeyPair();

            String privateKeyBase64 =
                    Base64.getEncoder().encodeToString(
                            pair.getPrivate().getEncoded()
                    );

            String publicKeyBase64 =
                    Base64.getEncoder().encodeToString(
                            pair.getPublic().getEncoded()
                    );

            String keyId =
                    "key_" +
                            hex(
                                    sha256(
                                            pair.getPublic()
                                                    .getEncoded()
                                    )
                            ).substring(0, 16);

            ObjectNode root =
                    JsonNodeFactory.instance.objectNode();

            root.put(
                    "key_id",
                    keyId
            );
            root.put(
                    "algorithm",
                    ALGORITHM
            );
            root.put(
                    "private_key_pkcs8_base64",
                    privateKeyBase64
            );
            root.put(
                    "public_key_x509_base64",
                    publicKeyBase64
            );

            Files.writeString(
                    keyPath,
                    jsonMapper
                            .writerWithDefaultPrettyPrinter()
                            .writeValueAsString(root)
            );

            return new KeyMaterial(
                    pair.getPrivate(),
                    pair.getPublic(),
                    keyId,
                    publicKeyBase64
            );
        }

        JsonNode root =
                jsonMapper.readTree(
                        Files.readString(keyPath)
                );

        byte[] privateBytes =
                Base64.getDecoder().decode(
                        root.path(
                                "private_key_pkcs8_base64"
                        ).asText()
                );

        byte[] publicBytes =
                Base64.getDecoder().decode(
                        root.path(
                                "public_key_x509_base64"
                        ).asText()
                );

        KeyFactory factory =
                KeyFactory.getInstance(ALGORITHM);

        PrivateKey loadedPrivate =
                factory.generatePrivate(
                        new PKCS8EncodedKeySpec(
                                privateBytes
                        )
                );

        PublicKey loadedPublic =
                factory.generatePublic(
                        new X509EncodedKeySpec(
                                publicBytes
                        )
                );

        return new KeyMaterial(
                loadedPrivate,
                loadedPublic,
                root.path("key_id").asText(),
                root.path(
                        "public_key_x509_base64"
                ).asText()
        );
    }

    private record KeyMaterial(
            PrivateKey privateKey,
            PublicKey publicKey,
            String keyId,
            String publicKeyBase64
    ) {
    }

    private static byte[] sha256(byte[] value)
            throws Exception {
        return MessageDigest
                .getInstance("SHA-256")
                .digest(value);
    }

    private static String hex(byte[] value) {
        StringBuilder result =
                new StringBuilder();

        for (byte b : value) {
            result.append(
                    String.format("%02x", b)
            );
        }

        return result.toString();
    }
}
