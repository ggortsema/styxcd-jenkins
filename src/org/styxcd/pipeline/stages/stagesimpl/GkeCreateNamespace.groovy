package org.styxcd.pipeline.stages.stagesimpl

class GkeCreateNamespace implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public GkeCreateNamespace(steps, featureFlags) {
        this.steps = steps
    }

    public void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]
        stageSpecificMap['TEST_VALUE'] = "IT WORKED"

        def yml = params['YML']
        steps.echo "here is yml"
        steps.echo "${yml}"

        steps.echo "----- STAGE PARAMS -----"

        params.each { key, value ->
            steps.echo "${key} = ${value}"
        }

        steps.echo "------------------------"


        steps.echo "Running ${this.class.simpleName}"

        def clusterName = "styxcd-sandbox-gke"
        def projectId = "styxcd-sandbox-grant"
        def clusterLocation = "us-east1-b"
        def namespace = "johnny-johnny"
        def credentialsId = "gcp-service-account"

        def gcloudConfig = "${steps.env.WORKSPACE}/.gcloud"
        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"

        steps.sh(script: "mkdir -p ${gcloudConfig}", returnStdout: true).trim()
        steps.sh(script: "mkdir -p ${steps.env.WORKSPACE}/.kube", returnStdout: true).trim()

        def authResult = null
        def credentialsResult = null

        steps.withCredentials([
                [$class: 'FileBinding', credentialsId: credentialsId, variable: 'GCP_KEY_FILE']
        ]) {

            authResult = steps.sh(
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} gcloud auth activate-service-account --key-file=\"\$GCP_KEY_FILE\"",
                    returnStdout: true
            ).trim()

            steps.echo("authResult: ${authResult}")

            credentialsResult = steps.sh(
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} KUBECONFIG=${kubeConfig} gcloud container clusters get-credentials ${clusterName} --zone ${clusterLocation} --project ${projectId}",
                    returnStdout: true
            ).trim()

            steps.echo("credentialsResult: ${credentialsResult}")
        }

        def nodesResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get nodes",
                returnStdout: true
        ).trim()

        steps.echo("nodesResult:")
        steps.echo(nodesResult)

        def namespaceCheckResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get namespace ${namespace} --ignore-not-found",
                returnStdout: true
        ).trim()

        steps.echo("namespaceCheckResult:")
        steps.echo(namespaceCheckResult)

        if (!namespaceCheckResult) {
            def namespaceCreateResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} create namespace ${namespace}",
                    returnStdout: true
            ).trim()

            steps.echo("namespaceCreateResult:")
            steps.echo(namespaceCreateResult)
        }

        def namespaceVerifyResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get namespace ${namespace}",
                returnStdout: true
        ).trim()

        steps.echo("namespaceVerifyResult:")
        steps.echo(namespaceVerifyResult)

    }
}
