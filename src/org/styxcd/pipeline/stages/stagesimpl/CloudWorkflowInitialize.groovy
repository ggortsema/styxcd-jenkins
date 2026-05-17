package org.styxcd.pipeline.stages.stagesimpl

import groovy.json.JsonOutput

class CloudWorkflowInitialize implements Serializable {

    def steps
    def mvnUtil

    CloudWorkflowInitialize(steps, featureFlags) {
        this.steps = steps
        this.mvnUtil = new org.styxcd.pipeline.utility.MavenUtil(steps, featureFlags)
    }

    void runStage(script, params, keyMaps) {

        keyMaps['BUILD_STATUS'] = 'SUCCESS'

        def callbackUrl = keyMaps['CALLBACK_URL']
        def executionId = keyMaps['EXECUTION_ID']

        if (callbackUrl && executionId) {
            def payload = [
                    executionId: executionId,
                    status     : "STARTED",
                    message    : "Jenkins execution started"
            ]

            try {
                steps.httpRequest(
                        url: callbackUrl,
                        httpMode: 'POST',
                        contentType: 'APPLICATION_JSON',
                        requestBody: JsonOutput.toJson(payload),
                        validResponseCodes: '200:299'
                )

                steps.echo "Sent STARTED callback for executionId ${executionId}"
            } catch (Exception e) {
                steps.echo "WARNING: Failed to send STARTED callback for executionId ${executionId}: ${e.message}"
            }

        } else {
            steps.echo "No callbackUrl or executionId found. Skipping STARTED callback."
        }

        steps.echo "IN INIT INSIDE METHOD"
        steps.cleanWs()

        params.each { entry ->
            steps.echo "Key: ${entry.key} Value: ${entry.value}"
        }

        def validateMap = params['VALIDATE_MAP']
        def yml = params['YML']

        steps.echo "here is yml"
        steps.echo "${yml}"

        steps.echo "in cloud workflow init stage"

        yml.release.applications.spring.each {
            keyMaps[it.name] = [:]
            mvnUtil.gitPull(script, it.repo, it.branch)

            keyMaps."${it.name}".'repo' = it.repo
            keyMaps."${it.name}".'branch' = it.branch

            steps.stash includes: '**', name: "${it.name}-pre-workspace", useDefaultExcludes: false
            steps.stash includes: '**', name: "${it.name}-workspace"
        }
    }
}