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

        steps.withCredentials([
                [$class: 'FileBinding', credentialsId: 'gcp-service-account', variable: 'GCP_KEY_FILE']
        ]) {
            steps.sh '''
        gcloud auth activate-service-account --key-file="$GCP_KEY_FILE"
        gcloud config set project styxcd-sandbox-grant
        gcloud container clusters list
    '''
        }
    }
}