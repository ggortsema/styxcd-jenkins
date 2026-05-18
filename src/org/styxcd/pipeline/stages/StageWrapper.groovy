package org.styxcd.pipeline.stages

import org.styxcd.pipeline.utility.MetricsUtil

class StageWrapper implements Serializable {

    def steps
    MetricsUtil metricsUtil

    StageWrapper(steps) {
        this.steps = steps
        this.metricsUtil = new MetricsUtil(steps)
    }

    def initializeBuildInformation(enabledFeatureFlags, keyMaps) {
        def startTime = System.currentTimeMillis()

        def buildMap = [:]
        buildMap["BUILD_TAG"] = "${steps.env.BUILD_TAG}"
        buildMap["JOB_NAME"] = "${steps.env.JOB_NAME}"
        buildMap["BUILD_ID"] = "${steps.env.BUILD_ID}"
        buildMap["JOB_URL"] = "${steps.env.JOB_URL}"
        buildMap["BUILD_USER_ID"] = "${steps.env.BUILD_USER_ID}"
        buildMap["BUILD_START_TIME"] = "${startTime}"
        buildMap["KHARONCD_VERSION"] = keyMaps['kharoncd_version']
        buildMap['FEATURE_FLAGS'] = enabledFeatureFlags

        keyMaps["BUILD_MAP"] = buildMap
        keyMaps["FEATURE_FLAGS"] = enabledFeatureFlags
    }

    def run(params, keyMaps, Map getStage, key) {

        String executionStageKey = key as String

        String genericStageKey =
                executionStageKey.contains(":") ?
                        executionStageKey.substring(
                                0,
                                executionStageKey.indexOf(":")
                        ) :
                        executionStageKey

        def stageFactory = getStage[genericStageKey]

        if (stageFactory == null) {
            steps.error(
                    "No stage registered for key: ${genericStageKey}. " +
                            "Original execution key: ${executionStageKey}"
            )
        }

        def stageLogic = stageFactory()

        def emitStageEvents =
                params.containsKey('emitStageEvents') ? params['emitStageEvents'] : true

        steps.stage(params['stagename']) {
            steps.node(params['label']) {

                Long startTime = System.currentTimeMillis()
                Long endTime
                String genericStageName = genericStageKey
                String instanceCountMapName = "${genericStageKey}_INSTANCE_COUNT"

                Integer count =
                        keyMaps[instanceCountMapName] ?
                                keyMaps[instanceCountMapName] + 1 :
                                1

                keyMaps[instanceCountMapName] = count

                String stageInstanceKey = "${genericStageKey}_${count}"
                String stageMapName = "STAGE_" + stageInstanceKey

                steps.echo "StageWrapper - execution key ${executionStageKey}"
                steps.echo "StageWrapper - generic stage key ${genericStageKey}"
                steps.echo "StageWrapper - running stage instance ${stageInstanceKey}"

                Map stageSpecificMap = [:]
                stageSpecificMap["STAGE_NAME"] = stageInstanceKey
                stageSpecificMap["EXECUTION_STAGE_KEY"] = executionStageKey
                stageSpecificMap["GENERIC_STAGE_NAME"] = genericStageName
                stageSpecificMap["NODE_NAME"] = "${steps.env.NODE_NAME}"
                stageSpecificMap["NODE_LABELS"] = "${steps.env.NODE_LABELS}"
                stageSpecificMap["START_TIME"] = startTime

                keyMaps[stageMapName] = stageSpecificMap
                keyMaps["STAGE_MAP_NAME"] = stageMapName

                if (emitStageEvents) {
                    Map startedEvent = metricsUtil.buildStageEvent(
                            "STAGE_STARTED",
                            "STARTED",
                            "Stage started",
                            params,
                            keyMaps,
                            stageInstanceKey,
                            executionStageKey,
                            params['stagename'] as String,
                            genericStageName,
                            count,
                            startTime
                    )

                    metricsUtil.emitStageEvent(startedEvent, keyMaps)
                }

                try {
                    stageLogic.runStage(steps, params, keyMaps)
                } catch (e) {

                    steps.echo "Error during processing of stage ${genericStageName} caused by ${e.message}"

                    endTime = System.currentTimeMillis()

                    stageSpecificMap["END_TIME"] = endTime
                    stageSpecificMap["ELAPSED_TIME"] = endTime - startTime
                    stageSpecificMap["STAGE_RESULT"] = 'FAILURE'
                    stageSpecificMap["STAGE_FAILURE_MESSAGE"] = e.message

                    keyMaps['BUILD_STATUS'] = 'FAILURE'
                    keyMaps['BUILD_FAILURE_MESSAGE'] = e.message

                    if (emitStageEvents) {
                        Map failedEvent = metricsUtil.buildStageEvent(
                                "STAGE_FAILED",
                                "FAILED",
                                "Stage failed",
                                params,
                                keyMaps,
                                stageInstanceKey,
                                executionStageKey,
                                params['stagename'] as String,
                                genericStageName,
                                count,
                                startTime,
                                endTime,
                                e
                        )

                        metricsUtil.emitStageEvent(failedEvent, keyMaps)
                    }

                    throw e
                } finally {
                    keyMaps["STAGE_MAP_NAME"] = null
                }

                endTime = System.currentTimeMillis()

                stageSpecificMap["END_TIME"] = endTime
                stageSpecificMap["ELAPSED_TIME"] = endTime - startTime
                stageSpecificMap["STAGE_RESULT"] = 'SUCCESS'

                if (emitStageEvents) {
                    Map succeededEvent = metricsUtil.buildStageEvent(
                            "STAGE_SUCCEEDED",
                            "SUCCEEDED",
                            "Stage succeeded",
                            params,
                            keyMaps,
                            stageInstanceKey,
                            executionStageKey,
                            params['stagename'] as String,
                            genericStageName,
                            count,
                            startTime,
                            endTime
                    )

                    metricsUtil.emitStageEvent(succeededEvent, keyMaps)
                }
            }
        }
    }
}