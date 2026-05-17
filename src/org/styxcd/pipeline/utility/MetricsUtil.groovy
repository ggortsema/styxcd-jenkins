package org.styxcd.pipeline.utility

import groovy.json.JsonOutput

class MetricsUtil implements Serializable {

    def steps

    static final String DEFAULT_LOKI_URL = "http://grafana.styxcd.com:3100/loki/api/v1/push"


    MetricsUtil(steps) {
        this.steps = steps
    }

    void emitStageEvent(Map event, Map keyMaps = [:]) {
        if (!event) {
            steps.echo "MetricsUtil - event cannot be null"
            return
        }

        Map lokiPayload = buildLokiPayload(event)

        steps.writeFile(
                file: 'styxcd_loki_event.json',
                text: JsonOutput.prettyPrint(JsonOutput.toJson(lokiPayload))
        )

        steps.echo "MetricsUtil - Loki event payload:"
        steps.echo steps.readFile('styxcd_loki_event.json')

        if (!isLokiEnabled(keyMaps)) {
            steps.echo "MetricsUtil - Loki publishing disabled"
            return
        }

        try {
            String lokiUrl = getLokiUrl(keyMaps)

            def response = steps.sh(
                    returnStdout: true,
                    script: """curl -sS -X POST '${lokiUrl}' \\
  -H 'Content-Type: application/json' \\
  --data-binary @styxcd_loki_event.json"""
            ).trim()

            steps.echo "MetricsUtil - Loki response: ${response}"
        } catch (e) {
            steps.echo "MetricsUtil - failed to publish Loki event: ${e.message}"
        }
    }

    Map buildStageEvent(
            String eventType,
            String status,
            String message,
            Map params,
            Map keyMaps,
            String stageId,
            String stageKey,
            String stageName,
            String genericStageName,
            Integer stageInstance,
            Long startedAtMillis,
            Long endedAtMillis = null,
            Throwable error = null,
            Map extraMetadata = [:]
    ) {
        Long elapsedMs = null
        if (startedAtMillis && endedAtMillis) {
            elapsedMs = endedAtMillis - startedAtMillis
        }

        Map event = [
                eventVersion    : "1.0",
                eventType       : eventType,
                source          : "jenkins",
                timestamp       : isoNow(),

                correlationId   : valueFrom(params, keyMaps, "correlationId", "executionId"),
                executionId     : valueFrom(params, keyMaps, "executionId", "correlationId"),

                workflow        : valueFrom(params, keyMaps, "workflow"),
                releaseName     : valueFrom(params, keyMaps, "releaseName"),
                releaseVersion  : valueFrom(params, keyMaps, "releaseVersion"),

                stageId         : stageId,
                stageKey        : stageKey,
                stageName       : stageName,
                genericStageName: genericStageName,
                stageInstance   : stageInstance,
                lifecycleType   : valueFrom(params, keyMaps, "lifecycleType") ?: "NORMAL",

                applicationName : valueFrom(params, keyMaps, "applicationName"),
                applicationType : valueFrom(params, keyMaps, "applicationType"),
                environment     : valueFrom(params, keyMaps, "environment"),
                cloudProvider   : valueFrom(params, keyMaps, "cloudProvider"),
                platform        : valueFrom(params, keyMaps, "platform"),

                status          : status,
                message         : message,

                startedAt       : startedAtMillis ? isoFromMillis(startedAtMillis) : null,
                endedAt         : endedAtMillis ? isoFromMillis(endedAtMillis) : null,
                elapsedMs       : elapsedMs,

                metadata        : buildMetadata(params, keyMaps, extraMetadata),
                errors          : error ? [buildError(error, stageName)] : []
        ]

        return removeNullValues(event)
    }

    Map buildLokiPayload(Map event) {
        String nowNanos = "${System.currentTimeMillis()}000000"

        Map labels = [
                job      : "styxcd-jenkins",
                source   : safeLabel(event.source),
                eventType: safeLabel(event.eventType),
                status   : safeLabel(event.status),
                workflow : safeLabel(event.workflow)
        ]

        if (event.environment) {
            labels.environment = safeLabel(event.environment)
        }

        if (event.platform) {
            labels.platform = safeLabel(event.platform)
        }

        if (event.applicationName) {
            labels.applicationName = safeLabel(event.applicationName)
        }

        return [
                streams: [
                        [
                                stream: labels,
                                values: [
                                        [
                                                nowNanos,
                                                JsonOutput.toJson(event)
                                        ]
                                ]
                        ]
                ]
        ]
    }

    private Map buildMetadata(Map params, Map keyMaps, Map extraMetadata) {
        Map metadata = [
                jenkinsJobName  : "${steps.env.JOB_NAME}",
                jenkinsBuildId  : "${steps.env.BUILD_ID}",
                jenkinsBuildTag : "${steps.env.BUILD_TAG}",
                jenkinsBuildUrl : "${steps.env.BUILD_URL ?: steps.env.JOB_URL}",
                nodeName        : "${steps.env.NODE_NAME}",
                nodeLabels      : "${steps.env.NODE_LABELS}",
                featureFlags    : keyMaps["FEATURE_FLAGS"] ?: keyMaps["feature_flags"] ?: params["feature_flags"]
        ]

        if (params["metadata"] instanceof Map) {
            metadata.putAll(params["metadata"] as Map)
        }

        if (extraMetadata) {
            metadata.putAll(extraMetadata)
        }

        return removeNullValues(metadata)
    }

    private Map buildError(Throwable error, String stageName) {
        return removeNullValues([
                code         : "STAGE_FAILED",
                message      : error.message,
                severity     : "ERROR",
                retryable    : false,
                exceptionType: error.getClass()?.getName(),
                details      : [
                        stageName: stageName
                ]
        ])
    }

    private boolean isLokiEnabled(Map keyMaps) {
        def value = keyMaps["LOKI_ENABLED"]
        if (value == null) {
            return true
        }

        return value.toString().toBoolean()
    }

    private String getLokiUrl(Map keyMaps) {
        return keyMaps["LOKI_URL"] ?: DEFAULT_LOKI_URL
    }

    private Object valueFrom(Map params, Map keyMaps, String primaryKey, String fallbackKey = null) {
        List keysToTry = [primaryKey]

        keysToTry.add(toUpperSnake(primaryKey))

        if (fallbackKey) {
            keysToTry.add(fallbackKey)
            keysToTry.add(toUpperSnake(fallbackKey))
        }

        for (String key : keysToTry) {
            if (params?.containsKey(key)) {
                return params[key]
            }

            if (keyMaps?.containsKey(key)) {
                return keyMaps[key]
            }
        }

        return null
    }

    private String toUpperSnake(String camelCase) {
        return camelCase
                .replaceAll(/([a-z])([A-Z])/, '$1_$2')
                .toUpperCase()
    }

    private String isoNow() {
        return isoFromMillis(System.currentTimeMillis())
    }

    private String isoFromMillis(Long millis) {
        return new Date(millis).format("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", TimeZone.getTimeZone("UTC"))
    }

    private String safeLabel(value) {
        if (value == null) {
            return "unknown"
        }

        return value.toString()
                .replaceAll("[^a-zA-Z0-9_:.-]", "_")
                .take(120)
    }

    private Map removeNullValues(Map input) {
        Map output = [:]

        input.each { key, value ->
            if (value == null) {
                return
            }

            if (value instanceof Map) {
                output[key] = removeNullValues(value as Map)
                return
            }

            if (value instanceof List) {
                output[key] = value
                return
            }

            output[key] = value
        }

        return output
    }
}