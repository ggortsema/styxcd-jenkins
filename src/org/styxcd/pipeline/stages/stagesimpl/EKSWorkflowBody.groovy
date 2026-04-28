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

        steps.echo "in eks body stage"

        //preparing to teardown eks cluster
        steps.withCredentials([
                steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def identity = steps.sh(
                    script: 'aws sts get-caller-identity',
                    returnStdout: true
            ).trim()

            steps.echo "AWS Identity: ${identity}"

            def clusterStatus = steps.sh(
                    script: 'eksctl get cluster --region us-east-1 --name johnny-johnny-dev',
                    returnStatus: true
            )

            steps.echo "Cluster status command returned: ${clusterStatus}"

            if (clusterStatus == 0) {
                steps.echo "Cluster exists. Updating kubeconfig."

                def kubeConfigOutput = steps.sh(
                        script: 'aws eks update-kubeconfig --region us-east-1 --name johnny-johnny-dev',
                        returnStdout: true
                ).trim()

                steps.echo "Kubeconfig output: ${kubeConfigOutput}"
            } else {
                steps.echo "Cluster does not exist. Nothing to teardown."
            }

        }


    }
}
