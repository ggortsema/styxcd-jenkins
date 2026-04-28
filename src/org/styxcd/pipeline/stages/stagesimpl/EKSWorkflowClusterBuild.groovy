package org.styxcd.pipeline.stages.stagesimpl

class EKSWorkflowClusterBuild implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowClusterBuild(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'build eks cluster'
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


        //building an eks cluster
        steps.echo "in eks worfklow cluster build stage"

        steps.withCredentials([
                steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def awsRegion = 'us-east-1'
            def clusterName = 'johnny-johnny-dev'
            def nodeType = 't3.small'
            def nodeCount = '1'

            def identity = steps.sh(
                    script: 'aws sts get-caller-identity',
                    returnStdout: true
            ).trim()
            steps.echo "AWS Identity: ${identity}"

            def clusterStatusBeforeBuild = steps.sh(
                    script: "eksctl get cluster --region ${awsRegion} --name ${clusterName}",
                    returnStatus: true
            )
            steps.echo "Cluster status before build: ${clusterStatusBeforeBuild}"

            if (clusterStatusBeforeBuild == 0) {
                steps.echo "Cluster already exists. Skipping cluster creation."
            } else {
                steps.echo "Cluster does not exist. Creating EKS cluster."

                def createClusterStatus = steps.sh(
                        script: "eksctl create cluster --name ${clusterName} --region ${awsRegion} --nodes ${nodeCount} --node-type ${nodeType} --managed",
                        returnStatus: true
                )
                steps.echo "Create cluster status: ${createClusterStatus}"

                if (createClusterStatus != 0) {
                    steps.error "EKS cluster creation failed with status: ${createClusterStatus}"
                }
            }

            steps.echo "Updating kubeconfig."

            def updateKubeconfigOutput = steps.sh(
                    script: "aws eks update-kubeconfig --region ${awsRegion} --name ${clusterName}",
                    returnStdout: true
            ).trim()
            steps.echo "Update kubeconfig output: ${updateKubeconfigOutput}"

            def nodes = steps.sh(
                    script: 'kubectl get nodes',
                    returnStdout: true
            ).trim()
            steps.echo "Kubernetes nodes:\n${nodes}"

            def clusterStatusAfterBuild = steps.sh(
                    script: "eksctl get cluster --region ${awsRegion} --name ${clusterName}",
                    returnStatus: true
            )
            steps.echo "Cluster status after build: ${clusterStatusAfterBuild}"
        }

    }
}
