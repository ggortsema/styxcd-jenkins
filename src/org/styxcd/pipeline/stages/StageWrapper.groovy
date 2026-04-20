package org.styxcd.pipeline.stages

import org.styxcd.pipeline.utility.MetricsUtil

class StageWrapper implements Serializable {

    def steps
    MetricsUtil metricsUtil

    public StageWrapper(steps) {
        this.steps = steps
        this.metricsUtil = new MetricsUtil(steps)
    }

    def initializeBuildInformation(enabledFeatureFlags, keyMaps) {
        // set up a map for splunk messages
        def startTime = System.currentTimeMillis()

        def splunkMap = [:]
        splunkMap["BUILD_TAG"] = "${steps.env.BUILD_TAG}"
        splunkMap["JOB_NAME"] = "${steps.env.JOB_NAME}"
        splunkMap["BUILD_ID"] = "${steps.env.BUILD_ID}"
        splunkMap["JOB_URL"] = "${steps.env.JOB_URL}"
        splunkMap["BUILD_USER_ID"] = "${steps.env.BUILD_USER_ID}"
        splunkMap["BUILD_START_TIME"] = "${startTime}"
        splunkMap["KHARONCD_VERSION"] = keyMaps['kharoncd_version']
        splunkMap['FEATURE_FLAGS'] = enabledFeatureFlags
        keyMaps["SPLUNK_MAP"] = splunkMap
    }

    def run(params, keyMaps, Map getStage, key) {

        def stageLogic = getStage[key]

        steps.stage(params['stagename']) {
            steps.node(params['label']) {


                def startTime = System.currentTimeMillis()
                def endTime
                def count
                def gnericStageName = key
                def instanceCountMapName = "${key}_INSTANCE_COUNT"

                if (keyMaps[instanceCountMapName]) {
                    count = keyMaps[instanceCountMapName] + 1
                    keyMaps[instanceCountMapName] = count
                } else {
                    count = 1
                    keyMaps[instanceCountMapName] = count
                }

                key = "${key}_${count}"

                steps.echo "IN StageWrapper with key of ${key}"


                def splunkStageMapName = "SPLUNK_STAGE_" + key
                def stageSpecificMap = [:]
                stageSpecificMap["STAGE_NAME"] = key
                stageSpecificMap["GENERIC_STAGE_NAME"] = gnericStageName


                keyMaps[splunkStageMapName] = stageSpecificMap
                keyMaps["STAGE_MAP_NAME"] = splunkStageMapName
                stageSpecificMap["NODE_NAME"] = "${steps.env.NODE_NAME}"
                stageSpecificMap["NODE_LABELS"] = "${steps.env.NODE_LABELS}"

                steps.echo "STAGE_MAP_NAME is ${splunkStageMapName}"
                def splunkMap = keyMaps["SPLUNK_MAP"]
                if (splunkMap) {
                    stageSpecificMap["BUILD_TAG"] = splunkMap["BUILD_TAG"]
                    stageSpecificMap["START_TIME"] = startTime
                }

                metricsUtil.addStageToSplunkMap(steps, "${key}", startTime, null, keyMaps)

                try {
                    stageLogic.runStage(steps, params, keyMaps)
                } catch (e) {

                    steps.echo "Error during processing of stage ${gnericStageName} caused by ${e.message}"

                    endTime = System.currentTimeMillis()
                    stageSpecificMap["END_TIME"] = endTime
                    stageSpecificMap["ELAPSED_TIME"] = endTime - startTime
                    stageSpecificMap["STAGE_RESULT"] = 'FAILURE'
                    stageSpecificMap["STAGE_FAILURE_MESSAGE"] = e.message
                    keyMaps['BUILD_STATUS'] = 'FAILURE'
                    keyMaps['BUILD_FAILURE_MESSAGE'] = e.message
                    throw e
                }

                endTime = System.currentTimeMillis()
                stageSpecificMap["END_TIME"] = endTime
                stageSpecificMap["ELAPSED_TIME"] = endTime - startTime
                stageSpecificMap["STAGE_RESULT"] = 'SUCCESS'

                metricsUtil.addStageToSplunkMap(steps, "${key}", startTime, endTime, keyMaps)

                keyMaps["STAGE_MAP_NAME"] = null
            }
        }
    }
}
