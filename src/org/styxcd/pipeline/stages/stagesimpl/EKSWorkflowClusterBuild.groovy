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

            def clusterStatusText = steps.sh(
                    script: "aws eks describe-cluster --region ${awsRegion} --name ${clusterName} --query cluster.status --output text 2>/dev/null || echo NOT_FOUND",
                    returnStdout: true
            ).trim()
            steps.echo "Cluster AWS status before build: ${clusterStatusText}"

            if (clusterStatusText == 'ACTIVE') {
                steps.echo "Cluster already exists and is ACTIVE. Skipping cluster creation."
            } else if (clusterStatusText == 'DELETING') {
                steps.echo "Cluster is still DELETING. Waiting for deletion to complete."

                def waitDeleteStatus = steps.sh(
                        script: "aws eks wait cluster-deleted --region ${awsRegion} --name ${clusterName}",
                        returnStatus: true
                )
                steps.echo "Wait for cluster deleted status: ${waitDeleteStatus}"

                if (waitDeleteStatus != 0) {
                    steps.error "Cluster was still deleting and did not finish cleanly with status: ${waitDeleteStatus}"
                }

                steps.echo "Cluster deletion completed. Creating EKS cluster."

                def createClusterStatus = steps.sh(
                        script: "eksctl create cluster --name ${clusterName} --region ${awsRegion} --nodes ${nodeCount} --node-type ${nodeType} --managed",
                        returnStatus: true
                )
                steps.echo "Create cluster status: ${createClusterStatus}"

                if (createClusterStatus != 0) {
                    steps.error "EKS cluster creation failed with status: ${createClusterStatus}"
                }
            } else if (clusterStatusText == 'NOT_FOUND') {
                steps.echo "Cluster does not exist. Creating EKS cluster."

                def createClusterStatus = steps.sh(
                        script: "eksctl create cluster --name ${clusterName} --region ${awsRegion} --nodes ${nodeCount} --node-type ${nodeType} --managed",
                        returnStatus: true
                )
                steps.echo "Create cluster status: ${createClusterStatus}"

                if (createClusterStatus != 0) {
                    steps.error "EKS cluster creation failed with status: ${createClusterStatus}"
                }
            } else if (clusterStatusText == 'CREATING') {
                steps.echo "Cluster is already CREATING. Waiting until it becomes ACTIVE."

                def waitActiveStatus = steps.sh(
                        script: "aws eks wait cluster-active --region ${awsRegion} --name ${clusterName}",
                        returnStatus: true
                )
                steps.echo "Wait for cluster active status: ${waitActiveStatus}"

                if (waitActiveStatus != 0) {
                    steps.error "Cluster did not become ACTIVE cleanly with status: ${waitActiveStatus}"
                }
            } else {
                steps.error "Cluster exists but is not usable. Current AWS status: ${clusterStatusText}"
            }

            def clusterActiveStatus = steps.sh(
                    script: "aws eks wait cluster-active --region ${awsRegion} --name ${clusterName}",
                    returnStatus: true
            )
            steps.echo "Final wait for cluster active status: ${clusterActiveStatus}"

            if (clusterActiveStatus != 0) {
                steps.error "Cluster is not ACTIVE after create/skip/wait flow. Status: ${clusterActiveStatus}"
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
                    script: "aws eks describe-cluster --region ${awsRegion} --name ${clusterName} --query cluster.status --output text",
                    returnStdout: true
            ).trim()
            steps.echo "Cluster AWS status after build: ${clusterStatusAfterBuild}"
        }

    }
}
