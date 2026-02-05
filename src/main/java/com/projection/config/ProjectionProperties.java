package com.projection.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "response.projection")
public class ProjectionProperties {

    private boolean enabled = true;
    private String headerName = "X-Response-Fields";
    private int maxDepth = 5;

    private CycleDetection cycleDetection = new CycleDetection();
    private Cache cache = new Cache();
    private TraceId traceId = new TraceId();
    private ErrorConfig error = new ErrorConfig();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHeaderName() {
        return headerName;
    }

    public void setHeaderName(String headerName) {
        this.headerName = headerName;
    }

    public int getMaxDepth() {
        return maxDepth;
    }

    public void setMaxDepth(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    public CycleDetection getCycleDetection() {
        return cycleDetection;
    }

    public void setCycleDetection(CycleDetection cycleDetection) {
        this.cycleDetection = cycleDetection;
    }

    public Cache getCache() {
        return cache;
    }

    public void setCache(Cache cache) {
        this.cache = cache;
    }

    public TraceId getTraceId() {
        return traceId;
    }

    public void setTraceId(TraceId traceId) {
        this.traceId = traceId;
    }

    public ErrorConfig getError() {
        return error;
    }

    public void setError(ErrorConfig error) {
        this.error = error;
    }

    public static class CycleDetection {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class Cache {
        private boolean enabled = true;
        private int defaultTtlSeconds = 60;
        private int collectionTtlSeconds = 10;
        private Conditional conditional = new Conditional();
        private ManualEviction manualEviction = new ManualEviction();
        private UserContext userContext = new UserContext();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getDefaultTtlSeconds() {
            return defaultTtlSeconds;
        }

        public void setDefaultTtlSeconds(int defaultTtlSeconds) {
            this.defaultTtlSeconds = defaultTtlSeconds;
        }

        public int getCollectionTtlSeconds() {
            return collectionTtlSeconds;
        }

        public void setCollectionTtlSeconds(int collectionTtlSeconds) {
            this.collectionTtlSeconds = collectionTtlSeconds;
        }

        public Conditional getConditional() {
            return conditional;
        }

        public void setConditional(Conditional conditional) {
            this.conditional = conditional;
        }

        public ManualEviction getManualEviction() {
            return manualEviction;
        }

        public void setManualEviction(ManualEviction manualEviction) {
            this.manualEviction = manualEviction;
        }

        public UserContext getUserContext() {
            return userContext;
        }

        public void setUserContext(UserContext userContext) {
            this.userContext = userContext;
        }

        public static class Conditional {
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        public static class ManualEviction {
            private boolean enabled = true;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }

        /**
         * User context configuration for per-user cache isolation.
         * The header name is used when @Projectable(userContext = true) is set.
         */
        public static class UserContext {
            private String headerName = "X-User-Id";

            public String getHeaderName() {
                return headerName;
            }

            public void setHeaderName(String headerName) {
                this.headerName = headerName;
            }
        }
    }

    public static class TraceId {
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    public static class ErrorConfig {
        private MissingField missingField = new MissingField();
        private MaxDepthError maxDepth = new MaxDepthError();
        private CycleError cycle = new CycleError();

        public MissingField getMissingField() {
            return missingField;
        }

        public void setMissingField(MissingField missingField) {
            this.missingField = missingField;
        }

        public MaxDepthError getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(MaxDepthError maxDepth) {
            this.maxDepth = maxDepth;
        }

        public CycleError getCycle() {
            return cycle;
        }

        public void setCycle(CycleError cycle) {
            this.cycle = cycle;
        }

        public static class MissingField {
            private int status = 400;

            public int getStatus() {
                return status;
            }

            public void setStatus(int status) {
                this.status = status;
            }
        }

        public static class MaxDepthError {
            private int status = 400;

            public int getStatus() {
                return status;
            }

            public void setStatus(int status) {
                this.status = status;
            }
        }

        public static class CycleError {
            private int status = 500;

            public int getStatus() {
                return status;
            }

            public void setStatus(int status) {
                this.status = status;
            }
        }
    }
}
