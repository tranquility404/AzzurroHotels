package com.tranquility.storage.adapter.supabase;

import com.tranquility.shared.storage.model.StorageBucket;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "supabase.storage")
public class SupabaseStorageProperties {

    private String projectUrl = "";
    private String serviceRoleKey = "";
    private Buckets buckets = new Buckets();

    public String getProjectUrl() {
        return projectUrl;
    }

    public void setProjectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
    }

    public String getServiceRoleKey() {
        return serviceRoleKey;
    }

    public void setServiceRoleKey(String serviceRoleKey) {
        this.serviceRoleKey = serviceRoleKey;
    }

    public Buckets getBuckets() {
        return buckets;
    }

    public void setBuckets(Buckets buckets) {
        this.buckets = buckets;
    }

    String bucketName(StorageBucket bucket) {
        if (bucket == null) {
            throw new IllegalArgumentException("A storage bucket is required.");
        }

        String name = switch (bucket) {
            case APP_BUCKET -> buckets.getAppBucket();
            case USER_BUCKET -> buckets.getUserBucket();
            case PUBLIC_ASSETS -> buckets.getPublicAssetsBucket();
            case TEMP_BUCKET -> buckets.getTempBucket();
        };

        if (name == null || name.isBlank() || name.contains("/")) {
            throw new IllegalStateException("The Supabase bucket name is not configured correctly.");
        }
        return name;
    }

    public static class Buckets {

        private String appBucket = "app";
        private String userBucket = "user-files";
        private String publicAssetsBucket = "public-assets";
        private String tempBucket = "temp";

        public String getAppBucket() {
            return appBucket;
        }

        public void setAppBucket(String appBucket) {
            this.appBucket = appBucket;
        }

        public String getUserBucket() {
            return userBucket;
        }

        public void setUserBucket(String userBucket) {
            this.userBucket = userBucket;
        }

        public String getPublicAssetsBucket() {
            return publicAssetsBucket;
        }

        public void setPublicAssetsBucket(String publicAssetsBucket) {
            this.publicAssetsBucket = publicAssetsBucket;
        }

        public String getTempBucket() {
            return tempBucket;
        }

        public void setTempBucket(String tempBucket) {
            this.tempBucket = tempBucket;
        }
    }
}
