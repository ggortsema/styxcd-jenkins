package org.styxcd.pipeline.stages.stagesimpl

class GkeValidateDeployment implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public GkeValidateDeployment(steps, featureFlags) {
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

        //TODO remove this parsing bridge later and get values directly from orchestrator
        yml.release?.environments?."${params['LIFECYCLE']}"?.each { target ->
            if (target?.name == params['TARGET_NAME']) {

                params['CLUSTER_NAME'] = target?.platform?.cluster_name
                params['PROJECT_ID'] = target?.platform?.project_id
                params['LOCATION'] = target?.platform?.location
                params['LOCATION_TYPE'] = target?.platform?.location_type
                params['NAMESPACE'] = target?.platform?.namespace
                params['CREDENTIALS_ID'] = target?.platform?.credentials?.id

                def targetApp = target?.platform?.applications?.find {it?.name == params['APP_NAME']}
                def releaseApp = yml.release?.applications?.values()?.flatten()?.find {it?.name == params['APP_NAME']}
                def dockerArtifact = releaseApp?.artifacts?.find {it?.type == 'docker-image'}

                params['IMAGE'] = dockerArtifact?.image
                params['REPLICAS'] = targetApp?.replicas ?: target?.platform?.defaults?.replicas
                params['SERVICE_TYPE'] = targetApp?.service?.type ?: target?.platform?.defaults?.service?.type
                params['CONTAINER_PORT'] = targetApp?.container?.port
                params['SERVICE_PORT'] = targetApp?.service?.port
                params['SERVICE_TARGET_PORT'] = targetApp?.service?.target_port
                params['DEPLOYMENT_NAME'] = params['APP_NAME']
                params['SERVICE_NAME'] = params['APP_NAME']
            }
        }

        def clusterName = params['CLUSTER_NAME']
        def projectId = params['PROJECT_ID']
        def location = params['LOCATION']
        def locationType = params['LOCATION_TYPE']
        def namespace = params['NAMESPACE']
        def credentialsId = params['CREDENTIALS_ID']
        def locationFlag = locationType == 'regional' ? '--region' : '--zone'
        def image = params['IMAGE']
        def replicas = params['REPLICAS']
        def serviceType = params['SERVICE_TYPE']
        def deploymentName = params['DEPLOYMENT_NAME']
        def serviceName = params['SERVICE_NAME']
        def containerPort = params['CONTAINER_PORT']
        def servicePort = params['SERVICE_PORT']
        def serviceTargetPort = params['SERVICE_TARGET_PORT']

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
                    script: "CLOUDSDK_CONFIG=${gcloudConfig} KUBECONFIG=${kubeConfig} gcloud container clusters get-credentials ${clusterName} ${locationFlag} ${location} --project ${projectId}",
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

        def deploymentSnapshotBefore = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get deployment ${deploymentName} -n ${namespace} -o wide",
                returnStdout: true
        ).trim()

        steps.echo("deploymentSnapshotBefore:")
        steps.echo(deploymentSnapshotBefore)

        def podsSnapshotBefore = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get pods -n ${namespace} -l app=${deploymentName} -o wide",
                returnStdout: true
        ).trim()

        steps.echo("podsSnapshotBefore:")
        steps.echo(podsSnapshotBefore)

        def rolloutResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} rollout status deployment/${deploymentName} -n ${namespace} --timeout=300s",
                returnStdout: true
        ).trim()

        steps.echo("rolloutResult:")
        steps.echo(rolloutResult)

        def deploymentSnapshotAfter = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get deployment ${deploymentName} -n ${namespace} -o wide",
                returnStdout: true
        ).trim()

        steps.echo("deploymentSnapshotAfter:")
        steps.echo(deploymentSnapshotAfter)

        def podsSnapshotAfter = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get pods -n ${namespace} -l app=${deploymentName} -o wide",
                returnStdout: true
        ).trim()

        steps.echo("podsSnapshotAfter:")
        steps.echo(podsSnapshotAfter)

    }
}
