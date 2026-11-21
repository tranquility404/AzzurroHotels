package com.tranquility.storage.adapter.supabase;

import com.tranquility.shared.storage.ObjectStorage;
import com.tranquility.shared.storage.ObjectStorageException;
import com.tranquility.shared.storage.model.StorageBucket;
import com.tranquility.shared.storage.model.StorageLocation;
import com.tranquility.shared.storage.model.UploadObject;
import com.tranquility.shared.storage.model.UploadResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.http.MediaType.APPLICATION_OCTET_STREAM;

@Component
public class SupabaseObjectStorage implements ObjectStorage {

    private final SupabaseStorageProperties properties;
    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    @Autowired
    public SupabaseObjectStorage(SupabaseStorageProperties properties) {
        this(properties, RestClient.builder().build(), JsonMapper.builder().build());
    }

    SupabaseObjectStorage(
            SupabaseStorageProperties properties,
            RestClient restClient,
            JsonMapper jsonMapper
    ) {
        this.properties = properties;
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    @Override
    public UploadResult upload(UploadObject object) {
        if (object == null || object.content() == null) {
            throw new IllegalArgumentException("An upload object with content is required.");
        }
        if (object.size() != object.content().length) {
            throw new IllegalArgumentException("The upload size must match the content length.");
        }

        URI uri = objectUri("/object", object.bucket(), object.path(), object.objectId());
        execute("upload", () -> restClient.post()
                .uri(uri)
                .headers(this::addAuthorizationHeaders)
                .contentType(contentType(object.contentType()))
                .header("x-upsert", "true")
                .body(object.content())
                .retrieve()
                .toBodilessEntity());

        return UploadResult.create(object);
    }

    @Override
    public InputStream download(StorageLocation location) {
        URI uri = objectUri("/object/authenticated", location.bucket(), location.path(), location.objectId());
        byte[] content = execute("download", () -> restClient.get()
                .uri(uri)
                .headers(this::addAuthorizationHeaders)
                .retrieve()
                .body(byte[].class));
        return new ByteArrayInputStream(content == null ? new byte[0] : content);
    }

    @Override
    public String generateAccessUrl(StorageLocation location, Duration expiresIn) {
        int expirySeconds = expirySeconds(expiresIn);
        URI uri = objectUri("/object/sign", location.bucket(), location.path(), location.objectId());
        String response = execute("create signed download URL", () -> restClient.post()
                .uri(uri)
                .headers(this::addAuthorizationHeaders)
                .contentType(APPLICATION_JSON)
                .body(jsonBody(Map.of("expiresIn", expirySeconds)))
                .retrieve()
                .body(String.class));
        return absoluteStorageUrl(requiredResponsePath(response, "signedURL", "signed download URL"));
    }

    @Override
    public String generateUploadUrl(StorageLocation location, String contentType, Duration expiresIn) {
        // Supabase signed-upload tokens authorize the object path. The eventual PUT must still
        // send the requested content type as its Content-Type header.
        contentType(contentType);
        expirySeconds(expiresIn); // Validate the contract even though Supabase sets token expiry server-side.
        URI uri = objectUri("/object/upload/sign", location.bucket(), location.path(), location.objectId());
        String response = execute("create signed upload URL", () -> restClient.post()
                .uri(uri)
                .headers(this::addAuthorizationHeaders)
                .retrieve()
                .body(String.class));
        return absoluteStorageUrl(requiredResponsePath(response, "url", "signed upload URL"));
    }

    @Override
    public String generatePublicUrl(StorageLocation location) {
        return storageApiBaseUrl() + "/object/public/" + encodeBucket(location.bucket()) + "/"
                + encodeObjectKey(location.path(), location.objectId());
    }

    @Override
    public void delete(StorageLocation location) {
        URI uri = objectUri("/object", location.bucket(), location.path(), location.objectId());
        execute("delete", () -> restClient.delete()
                .uri(uri)
                .headers(this::addAuthorizationHeaders)
                .retrieve()
                .toBodilessEntity());
    }

    private URI objectUri(String endpoint, StorageBucket bucket, String path, String objectId) {
        return URI.create(storageApiBaseUrl() + endpoint + "/" + encodeBucket(bucket) + "/"
                + encodeObjectKey(path, objectId));
    }

    private String encodeBucket(StorageBucket bucket) {
        return UriUtils.encodePathSegment(properties.bucketName(bucket), StandardCharsets.UTF_8);
    }

    private String encodeObjectKey(String path, String objectId) {
        String safePath = validatePath(path);
        String safeObjectId = validateObjectId(objectId);
        String key = safePath.isEmpty() ? safeObjectId : safePath + "/" + safeObjectId;
        return UriUtils.encodePath(key, StandardCharsets.UTF_8);
    }

    private String validatePath(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        if (path.startsWith("/") || path.endsWith("/") || path.contains("\\") || path.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The storage path must be a relative object path.");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.isBlank() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("The storage path contains an invalid segment.");
            }
        }
        return path;
    }

    private String validateObjectId(String objectId) {
        if (objectId == null || objectId.isBlank() || objectId.contains("/") || objectId.contains("\\")
                || objectId.equals(".") || objectId.equals("..") || objectId.indexOf('\0') >= 0) {
            throw new IllegalArgumentException("The storage object id is invalid.");
        }
        return objectId;
    }

    private String storageApiBaseUrl() {
        String projectUrl = properties.getProjectUrl();
        if (projectUrl == null || projectUrl.isBlank()) {
            throw new ObjectStorageException(
                    "Supabase Storage is not configured. Set SUPABASE_URL.",
                    new IllegalStateException("Missing Supabase project URL."));
        }

        URI uri;
        try {
            uri = URI.create(projectUrl.trim());
        } catch (IllegalArgumentException exception) {
            throw new ObjectStorageException("The Supabase project URL is invalid.", exception);
        }
        String scheme = uri.getScheme();
        boolean secureScheme = "https".equalsIgnoreCase(scheme);
        boolean localHttp = "http".equalsIgnoreCase(scheme)
                && ("localhost".equalsIgnoreCase(uri.getHost())
                || "127.0.0.1".equals(uri.getHost())
                || "::1".equals(uri.getHost()));
        if (!uri.isAbsolute() || uri.getHost() == null || !(secureScheme || localHttp)
                || uri.getRawUserInfo() != null
                || (uri.getRawPath() != null && !uri.getRawPath().isEmpty() && !uri.getRawPath().equals("/"))
                || uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new ObjectStorageException("The Supabase project URL must be an HTTPS project origin (or local HTTP).",
                    new IllegalArgumentException("Invalid Supabase project URL."));
        }

        String origin = projectUrl.trim().replaceAll("/+$", "");
        return origin + "/storage/v1";
    }

    private void addAuthorizationHeaders(org.springframework.http.HttpHeaders headers) {
        String key = properties.getServiceRoleKey();
        if (key == null || key.isBlank()) {
            throw new ObjectStorageException(
                    "Supabase Storage is not configured. Set SUPABASE_SERVICE_ROLE_KEY.",
                    new IllegalStateException("Missing Supabase service-role key."));
        }
        headers.set("apikey", key);
        headers.setBearerAuth(key);
    }

    private String absoluteStorageUrl(String returnedPath) {
        if (returnedPath == null || returnedPath.isBlank()) {
            throw new ObjectStorageException("Supabase returned an empty signed URL.",
                    new IllegalStateException("Missing signed URL."));
        }
        String value = returnedPath.trim();
        if (value.startsWith("//")) {
            throw new ObjectStorageException("Supabase returned an invalid signed URL.",
                    new IllegalStateException("Protocol-relative URL is not accepted."));
        }
        if (value.startsWith("https://") || value.startsWith("http://")) {
            if (!value.startsWith(storageApiBaseUrl() + "/")) {
                throw new ObjectStorageException("Supabase returned a signed URL for an unexpected host.",
                        new IllegalStateException("Unexpected signed URL host."));
            }
            return value;
        }
        return storageApiBaseUrl() + (value.startsWith("/") ? value : "/" + value);
    }

    private String requiredResponsePath(String responseBody, String propertyName, String operation) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new ObjectStorageException("Supabase returned an empty response while attempting to " + operation + ".",
                    new IllegalStateException("Empty Supabase response."));
        }
        try {
            JsonNode response = jsonMapper.readTree(responseBody);
            JsonNode value = response.get(propertyName);
            if (value == null || value.isNull() || value.asString().isBlank()) {
                throw new ObjectStorageException("Supabase did not return the expected URL for " + operation + ".",
                        new IllegalStateException("Missing response property: " + propertyName));
            }
            return value.asString();
        } catch (ObjectStorageException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ObjectStorageException("Supabase returned an unreadable response while attempting to " + operation + ".",
                    exception);
        }
    }

    private String jsonBody(Map<String, ?> body) {
        try {
            return jsonMapper.writeValueAsString(body);
        } catch (Exception exception) {
            throw new ObjectStorageException("Could not prepare the Supabase Storage request.", exception);
        }
    }

    private org.springframework.http.MediaType contentType(String value) {
        if (value == null || value.isBlank()) {
            return APPLICATION_OCTET_STREAM;
        }
        try {
            return org.springframework.http.MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("The upload content type is invalid.", exception);
        }
    }

    private int expirySeconds(Duration duration) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            throw new IllegalArgumentException("The signed URL expiry must be a positive duration.");
        }
        try {
            long seconds = duration.toSeconds();
            if (seconds < 1 || seconds > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("The signed URL expiry must be between 1 second and 2^31-1 seconds.");
            }
            return (int) seconds;
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("The signed URL expiry is too large.", exception);
        }
    }

    private <T> T execute(String operation, Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException exception) {
            throw new ObjectStorageException(
                    "Supabase Storage " + operation + " failed with HTTP " + exception.getStatusCode().value() + ".",
                    exception);
        } catch (RestClientException exception) {
            throw new ObjectStorageException("Supabase Storage " + operation + " failed.", exception);
        }
    }
}
