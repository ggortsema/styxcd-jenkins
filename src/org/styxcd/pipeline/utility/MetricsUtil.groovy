package org.styxcd.pipeline.utility

import groovy.json.JsonOutput

class MetricsUtil implements Serializable {

    def steps

    static final SPLUNK_ENDPOINT = "https://hec.splunk.mclocal.int:1337/services/collector/raw"

    public MetricsUtil(steps) { this.steps = steps }

    public void addStageToSplunkMap(script, String stageName, startTime, endTime, Map keyMaps) {

        if (!stageName) {
            steps.echo "in metrics util - stageName cannot be null"
            return
        }

        steps.echo "in metrics util with stagename - ${stageName}"

        if ((stageName.toLowerCase().contains("initialize") || stageName.toLowerCase().contains("cleanup")) && !stageName.contains('*')) {
            steps.echo "in metrics util - this is an initilize or cleanup method so we do not want to do anything"
            return
        }

        if (stageName.contains('*')) {
            stageName = stageName.replace('*', '')
        }

        def splunkMap = keyMaps["SPLUNK_MAP"]
        def stages
        def stageMap

        if (splunkMap["STAGES"]) {
            stages = splunkMap["STAGES"]
        } else {
            stages = [:]
            splunkMap["STAGES"] = stages
            steps.echo "No stage object found in the SplunkMap. Defaulting to [:]"
        }

        if (stages[stageName]) {
            stageMap = stages[stageName]
        } else {
            stageMap = [:]
            stages[stageName] = stageMap
            steps.echo "The following stage is missing from the stageMap: ${stageName}. Defaulting to [:]"
        }

        steps.echo "HERE ARE START AND END TIMES: ${startTime} - ${endTime}"
        if (startTime) stageMap["START_TIME"] = startTime
        if (endTime) stageMap["END_TIME"] = endTime
        if (startTime && endTime) stageMap["ELAPSED_TIME"] = endTime - startTime
    }

    public Map sendJSONToSplunk(script, Map splunkJSON, Map alerts = [:], String token = 'REDACTED') {

        def data = steps.readJSON text: JsonOutput.toJson(splunkJSON)
        steps.writeJSON(file: 'splunk_data.json', json: data, pretty: 4)
        def jsonFileString = steps.readFile('splunk_data.json')
        steps.echo "splunk_data: "
        steps.echo "${jsonFileString}"

        try {
            alerts["SPLUNK_RESPONSE"] = "SPLUNK DISABLED"
            //alerts["SPLUNK_RESPONSE"] = steps.sh([returnStdout: true, script: "curl -f --data \"@splunk_data.json\" -k ${SPLUNK_ENDPOINT} -H \"Authorization: Splunk ${token}\""])
        } catch (e) {
            alerts["SPLUNK_RESPONSE"] = e
        }
        steps.echo "Splunk output: ${alerts["SPLUNK_RESPONSE"]}"
        return alerts
    }
}
