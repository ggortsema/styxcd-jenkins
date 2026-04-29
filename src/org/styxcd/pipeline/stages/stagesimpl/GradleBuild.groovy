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
        params['stagename'] = 'gradle build and test'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]
        stageSpecificMap['TEST_VALUE'] = "IT WORKED"

        def yml = params['YML']
        steps.echo "here is yml"
        steps.echo "${yml}"


        //your stage work goes here
        steps.echo "in gradle build stage"

        steps.dir('styxcd-jenkins') {
            steps.git(
                    branch: 'main',
                    url: 'https://github.com/ggortsema/styxcd-jenkins.git'
            )

            def testStatus = steps.sh(
                    script: './gradlew clean test',
                    returnStatus: true
            )
            steps.echo "Shared library test status: ${testStatus}"

            steps.junit(
                    testResults: 'build/test-results/test/*.xml',
                    allowEmptyResults: false
            )

            steps.publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'build/reports/tests/test',
                    reportFiles: 'index.html',
                    reportName: 'Gradle Test Report'
            ])

            steps.publishHTML([
                    allowMissing: false,
                    alwaysLinkToLastBuild: true,
                    keepAll: true,
                    reportDir: 'build/spock-reports',
                    reportFiles: 'index.html',
                    reportName: 'Spock Test Report'
            ])

            if (testStatus != 0) {
                steps.error "Shared library tests failed with status: ${testStatus}"
            }
        }



    }
}
