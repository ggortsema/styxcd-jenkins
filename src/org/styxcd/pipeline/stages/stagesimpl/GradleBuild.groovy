package org.styxcd.pipeline.stages.stagesimpl

class GradleBuild implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public GradleBuild(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['appHostName'] = paramMap['APPHOST_NAME']
        params['stagename'] = "Build Gradle App - ${paramMap['APPHOST_NAME']}"
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]
        stageSpecificMap['TEST_VALUE'] = "IT WORKED"


        params.each { entry ->
            steps.echo "Key: ${entry.key} Value: ${entry.value}"
        }

        def yml = params['YML']
        steps.echo "here is yml"
        steps.echo "${yml}"

        steps.echo "in gradle build stage"

        yml.release.applications.spring.each {

            if (it?.name == params['appHostName']) {
                steps.deleteDir()

                //stage specific splunk information
                stageSpecificMap["GIT_REPO"] = keyMaps."${params['appHostName']}".'repo'
                stageSpecificMap["GIT_BRANCH"] = keyMaps."${params['appHostName']}".'branch'

                steps.echo "repo: " + stageSpecificMap["GIT_REPO"]
                steps.echo "branch: " + stageSpecificMap["GIT_BRANCH"]

                steps.unstash "${it.name}-pre-workspace"

                def keyMap = keyMaps[it.name]

                def testStatus = steps.sh(
                        script: './gradlew clean test',
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
                    steps.error "Shared library tests failed with status: ${testStatus}"
                }

            }
        }

        steps.stash includes: '**', name: "${it.name}-workspace"

    }
}
