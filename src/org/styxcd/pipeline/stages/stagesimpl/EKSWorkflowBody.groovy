package org.styxcd.pipeline.stages.stagesimpl

class EKSWorkflowBody implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowBody(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'body'
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
        steps.echo "in eks body stage"

        def identity = steps.sh(
                script: 'aws sts get-caller-identity',
                returnStdout: true
        ).trim()

        steps.echo "AWS Identity: ${identity}"


    }
}
