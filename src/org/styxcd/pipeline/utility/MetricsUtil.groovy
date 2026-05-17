package org.styxcd.pipeline.utility

import groovy.json.JsonOutput

class MetricsUtil implements Serializable {

    def steps

    static final SPLUNK_ENDPOINT = "https://hec.splunk.mclocal.int:1337/services/collector/raw"

    MetricsUtil(steps) {
        this.steps = steps
    }

    void addStageToSplunkMap(script, String stageName, startTime, endTime, Map keyMaps) {

        if (!stageName) {
            steps.echo "MetricsUtil - stageName cannot be null"
            return
        }

        def normalizedStageName = normalizeStageName(stageName)

        steps.echo "MetricsUtil - recording metrics for stage: ${normalizedStageName}"

        def splunkMap = keyMaps["SPLUNK_MAP"]
        if (!splunkMap) {
            splunkMap = [:]
            keyMaps["SPLUNK_MAP"] = splunkMap
            steps.echo "MetricsUtil - SPLUNK_MAP was missing. Defaulting to [:]"
        }

        def stages = splunkMap["STAGES"]
        if (!stages) {
            stages = [:]
            splunkMap["STAGES"] = stages
            steps.echo "MetricsUtil - STAGES was missing. Defaulting to [:]"
        }

        def stageMap = stages[normalizedStageName]
        if (!stageMap) {
            stageMap = [:]
            stages[normalizedStageName] = stageMap
            steps.echo "MetricsUtil - stage map missing for ${normalizedStageName}. Defaulting to [:]"
        }

        steps.echo "MetricsUtil - start/end times: ${startTime} - ${endTime}"

        if (startTime) {
            stageMap["START_TIME"] = startTime
        }

        if (endTime) {
            stageMap["END_TIME"] = endTime
        }

        if (startTime && endTime) {
            stageMap["ELAPSED_TIME"] = endTime - startTime
        }
    }

    Map sendJSONToSplunk(script, Map splunkJSON, Map alerts = [:], String token = 'REDACTED') {

        def data = steps.readJSON text: JsonOutput.toJson(splunkJSON)

        steps.writeJSON(file: 'splunk_data.json', json: data, pretty: 4)

        def jsonFileString = steps.readFile('splunk_data.json')
        steps.echo "splunk_data:"
        steps.echo "${jsonFileString}"

        try {
            alerts["SPLUNK_RESPONSE"] = "SPLUNK DISABLED"

            // alerts["SPLUNK_RESPONSE"] = steps.sh(
            //     returnStdout: true,
            //     script: "curl -f --data \"@splunk_data.json\" -k ${SPLUNK_ENDPOINT} -H \"Authorization: Splunk ${token}\""
            // )
        } catch (e) {
            alerts["SPLUNK_RESPONSE"] = e
        }

        steps.echo "Splunk output: ${alerts["SPLUNK_RESPONSE"]}"

        return alerts
    }

    private String normalizeStageName(String stageName) {
        return stageName?.replace('*', '')
    }
}