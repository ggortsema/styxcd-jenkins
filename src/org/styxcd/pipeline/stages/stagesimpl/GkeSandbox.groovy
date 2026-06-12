package org.styxcd.pipeline.stages.stagesimpl

class GkeSandbox implements Serializable {

    def steps

    GkeSandbox(steps, featureFlags) {
        this.steps = steps
    }

    void runStage(script, params, keyMaps) {

        def stageMapName = keyMaps["STAGE_MAP_NAME"]
        def stageSpecificMap = keyMaps[stageMapName]

        if (stageSpecificMap == null) {
            stageSpecificMap = [:]
            keyMaps[stageMapName] = stageSpecificMap
        }

        steps.echo "IN GKE SANDBOX STAGE"

        def gcloudConfig = "${steps.env.WORKSPACE}/.gcloud"
        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"

        def mkdirGcloudResult = steps.sh(
                script: "mkdir -p ${gcloudConfig}",
                returnStdout: true
        ).trim()

        steps.echo("mkdirGcloudResult: ${mkdirGcloudResult}")

        def mkdirKubeResult = steps.sh(
                script: "mkdir -p ${steps.env.WORKSPACE}/.kube",
                returnStdout: true
        ).trim()

        steps.echo("mkdirKubeResult: ${mkdirKubeResult}")

        def authResult = null
        def credentialsResult = null

        steps.withCredentials([
                [$class: 'FileBinding', credentialsId: 'gcp-service-account', variable: 'GCP_KEY_FILE']
        ]) {

            authResult = steps.sh(
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} gcloud auth activate-service-account --key-file=\"\$GCP_KEY_FILE\"",
                    returnStdout: true
            ).trim()

            steps.echo("authResult: ${authResult}")

            credentialsResult = steps.sh(
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} KUBECONFIG=${kubeConfig} gcloud container clusters get-credentials styxcd-sandbox-gke --zone us-east1-b --project styxcd-sandbox-grant",
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
    }
}