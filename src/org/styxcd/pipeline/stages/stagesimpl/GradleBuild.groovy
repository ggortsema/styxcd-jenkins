package org.styxcd.pipeline.stages.stagesimpl

class GradleBuild implements Serializable {

    def steps

    GradleBuild(steps, featureFlags) {
        this.steps = steps
    }

    void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]

        if (stageSpecificMap == null) {
            stageSpecificMap = [:]
            keyMaps[stageMapName] = stageSpecificMap
        }

        def appName = params["appName"] ?: params["appHostName"]
        def repo = params["repo"]
        def branch = params["branch"] ?: "main"

        def buildCommand =
                params["buildCommand"] ?: "./gradlew clean test"

        if (!appName) {
            steps.error "Gradle build stage requires appName or appHostName"
        }

        if (!repo) {
            steps.error "Gradle build stage requires repo for ${appName}"
        }

        def preWorkspaceStashName =
                params["preWorkspaceStashName"] ?:
                        "${appName}-pre-workspace"

        def workspaceStashName =
                params["workspaceStashName"] ?:
                        "${appName}-workspace"

        steps.echo "in gradle build stage"
        steps.echo "appName: ${appName}"
        steps.echo "repo: ${repo}"
        steps.echo "branch: ${branch}"
        steps.echo "buildCommand: ${buildCommand}"

        steps.deleteDir()

        stageSpecificMap["GIT_REPO"] = repo
        stageSpecificMap["GIT_BRANCH"] = branch

        steps.unstash "${preWorkspaceStashName}"

        def testStatus = steps.sh(
                script: buildCommand,
                returnStatus: true
        )

        steps.junit(
                testResults: 'build/test-results/test/*.xml',
                allowEmptyResults: false
        )

        steps.publishHTML([
                allowMissing         : false,
                alwaysLinkToLastBuild: true,
                keepAll              : true,
                reportDir            : 'build/reports/tests/test',
                reportFiles          : 'index.html',
                reportName           : 'Gradle Test Report'
        ])

        steps.publishHTML([
                allowMissing         : false,
                alwaysLinkToLastBuild: true,
                keepAll              : true,
                reportDir            : 'build/spock-reports',
                reportFiles          : 'index.html',
                reportName           : 'Spock Test Report'
        ])

        if (testStatus != 0) {
            steps.error(
                    "Gradle build failed for ${appName} with status: ${testStatus}"
            )
        }

        steps.stash(
                includes: '**',
                name: workspaceStashName,
                useDefaultExcludes: false
        )
    }
}