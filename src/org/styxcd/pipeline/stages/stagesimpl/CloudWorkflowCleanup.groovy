package org.styxcd.pipeline.stages.stagesimpl

import groovy.json.JsonOutput

class CloudWorkflowCleanup implements Serializable {

    def steps

    CloudWorkflowCleanup(steps, featureFlags) {
        this.steps = steps
    }

    void runStage(script, params, keyMaps) {

        params.each { entry ->
            steps.echo "Key: ${entry.key} Value: ${entry.value}"
        }

        steps.echo "in cloud workflow cleanup stage"

        def callbackUrl = keyMaps['CALLBACK_URL']
        def executionId = keyMaps['EXECUTION_ID']

        if (callbackUrl && executionId) {
            def buildStatus = keyMaps['BUILD_STATUS'] ?: steps.currentBuild.currentResult ?: 'SUCCESS'
            def orchestratorStatus = buildStatus == 'SUCCESS' ? 'SUCCESS' : 'FAILED'

            def payload = [
                    executionId: executionId,
                    status     : orchestratorStatus,
                    message    : "Jenkins execution completed with status ${buildStatus}"
            ]

            try {
                steps.httpRequest(
                        url: callbackUrl,
                        httpMode: 'POST',
                        contentType: 'APPLICATION_JSON',
                        requestBody: JsonOutput.toJson(payload),
                        validResponseCodes: '200:299'
                )

                steps.echo "Sent ${orchestratorStatus} callback for executionId ${executionId}"
            } catch (Exception e) {
                steps.echo "WARNING: Failed to send ${orchestratorStatus} callback for executionId ${executionId}: ${e.message}"
            }

        } else {
            steps.echo "No callbackUrl or executionId found. Skipping final callback."
        }
    }
}