package org.styxcd.pipeline.stages.stagesimpl

class EKSWorkflowClusterTeardown implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowClusterTeardown(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'eks teardown cluster'
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

        steps.echo "in eks cluster teardown stage"

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

                def nodes = steps.sh(
                        script: 'kubectl get nodes',
                        returnStdout: true
                ).trim()

                steps.echo "Kubernetes nodes:\n${nodes}"

            } else {
                steps.echo "Cluster does not exist. Nothing to teardown."
            }

        }

        steps.dir('johnny-johnny-deployment') {
            steps.git(
                    branch: 'main',
                    url: 'https://github.com/ggortsema/johnny-johnny-deployment.git'
            )

            def files = steps.sh(
                    script: 'ls -la eks/dev',
                    returnStdout: true
            ).trim()

            steps.echo "Deployment files:\n${files}"
        }

        steps.withCredentials([
                steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def clusterStatusBeforeDelete = steps.sh(
                    script: 'eksctl get cluster --region us-east-1 --name johnny-johnny-dev',
                    returnStatus: true
            )
            steps.echo "Cluster status before delete: ${clusterStatusBeforeDelete}"

            if (clusterStatusBeforeDelete == 0) {
                steps.echo "Cluster exists. Updating kubeconfig before deleting resources."

                def updateKubeconfigOutput = steps.sh(
                        script: 'aws eks update-kubeconfig --region us-east-1 --name johnny-johnny-dev',
                        returnStdout: true
                ).trim()
                steps.echo "Update kubeconfig output: ${updateKubeconfigOutput}"

                def deleteIngressStatus = steps.sh(
                        script: 'kubectl delete -f johnny-johnny-deployment/eks/dev/ingress.yml --ignore-not-found=true',
                        returnStatus: true
                )
                steps.echo "Delete ingress status: ${deleteIngressStatus}"

                def deleteFrontendStatus = steps.sh(
                        script: 'kubectl delete -f johnny-johnny-deployment/eks/dev/frontend.yml --ignore-not-found=true',
                        returnStatus: true
                )
                steps.echo "Delete frontend status: ${deleteFrontendStatus}"

                def deleteBackendStatus = steps.sh(
                        script: 'kubectl delete -f johnny-johnny-deployment/eks/dev/backend.yml --ignore-not-found=true',
                        returnStatus: true
                )
                steps.echo "Delete backend status: ${deleteBackendStatus}"

                def deleteClusterStatus = steps.sh(
                        script: 'eksctl delete cluster --region us-east-1 --name johnny-johnny-dev',
                        returnStatus: true
                )
                steps.echo "Delete cluster status: ${deleteClusterStatus}"
            } else {
                steps.echo "Cluster does not exist. Skipping manifest and cluster delete."
            }

            def clusterStatusAfterDelete = steps.sh(
                    script: 'eksctl get cluster --region us-east-1 --name johnny-johnny-dev',
                    returnStatus: true
            )

            steps.echo "Cluster status after delete: ${clusterStatusAfterDelete}"
        }




    }
}
