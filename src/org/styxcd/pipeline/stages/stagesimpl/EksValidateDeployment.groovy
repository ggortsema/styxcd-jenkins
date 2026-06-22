package org.styxcd.pipeline.stages.stagesimpl

class EksValidateDeployment implements Serializable {

    def steps

    public EksValidateDeployment(steps, featureFlags) {
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

        def targetConfig = null

        // TODO remove this parsing bridge later and get values directly from orchestrator
        yml.release?.environments?."${params['LIFECYCLE']}"?.each { target ->
            if (target?.name == params['TARGET_NAME'] && target?.platform?.name == 'eks') {
                targetConfig = target

                params['AWS_REGION'] = target?.platform?.region
                params['CLUSTER_NAME'] = target?.platform?.cluster_name
                params['NAMESPACE'] = target?.platform?.namespace
                params['AWS_ACCESS_KEY_ID_CREDENTIAL'] = target?.platform?.credentials?.access_key_id
                params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] = target?.platform?.credentials?.secret_access_key
            }
        }

        if (targetConfig == null) {
            steps.error "Could not find EKS target config for TARGET_NAME ${params['TARGET_NAME']} and LIFECYCLE ${params['LIFECYCLE']}"
        }

        def applicationName = params['APPLICATION_NAME']

        if (!applicationName?.trim()) {
            steps.error "Missing APPLICATION_NAME"
        }

        def targetApplicationConfig = targetConfig?.platform?.applications?.find {
            it?.name == applicationName
        }

        if (targetApplicationConfig == null) {
            steps.error "Could not find target application config for APPLICATION_NAME ${applicationName}"
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def namespace = params['NAMESPACE'] ?: 'default'
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        def deploymentName = applicationName
        def serviceName = applicationName

        if (!awsRegion?.trim()) {
            steps.error "Missing AWS_REGION for EKS target ${params['TARGET_NAME']}"
        }

        if (!clusterName?.trim()) {
            steps.error "Missing CLUSTER_NAME for EKS target ${params['TARGET_NAME']}"
        }

        if (!namespace?.trim()) {
            steps.error "Missing NAMESPACE for EKS target ${params['TARGET_NAME']}"
        }

        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"

        steps.sh(script: "mkdir -p ${steps.env.WORKSPACE}/.kube", returnStdout: true).trim()

        steps.withCredentials([
                steps.string(credentialsId: awsAccessKeyCredential, variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: awsSecretKeyCredential, variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            def identity = steps.sh(
                    script: 'aws sts get-caller-identity',
                    returnStdout: true
            ).trim()

            steps.echo "AWS Identity:"
            steps.echo identity

            def kubeconfigResult = steps.sh(
                    script: "KUBECONFIG=${kubeConfig} aws eks update-kubeconfig --region ${awsRegion} --name ${clusterName}",
                    returnStdout: true
            ).trim()

            steps.echo "kubeconfigResult:"
            steps.echo kubeconfigResult

            def nodesResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get nodes",
                    returnStdout: true
            ).trim()

            steps.echo "nodesResult:"
            steps.echo nodesResult

            def deploymentSnapshotBefore = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get deployment ${deploymentName} -n ${namespace} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "deploymentSnapshotBefore:"
            steps.echo deploymentSnapshotBefore

            def podsSnapshotBefore = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get pods -n ${namespace} -l app=${deploymentName} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "podsSnapshotBefore:"
            steps.echo podsSnapshotBefore

            def serviceSnapshotBefore = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get service ${serviceName} -n ${namespace} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "serviceSnapshotBefore:"
            steps.echo serviceSnapshotBefore

            def rolloutResult

            try {
                rolloutResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} rollout status deployment/${deploymentName} -n ${namespace} --timeout=300s",
                        returnStdout: true
                ).trim()
            } catch (Exception ex) {

                def podResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} get pods -n ${namespace} -o wide",
                        returnStdout: true
                ).trim()

                steps.echo "podResult:"
                steps.echo podResult

                def describeDeploymentResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} describe deployment ${deploymentName} -n ${namespace}",
                        returnStdout: true
                ).trim()

                steps.echo "describeDeploymentResult:"
                steps.echo describeDeploymentResult

                def describePodsResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} describe pods -n ${namespace} -l app=${deploymentName}",
                        returnStdout: true
                ).trim()

                steps.echo "describePodsResult:"
                steps.echo describePodsResult

                throw ex
            }

            steps.echo "rolloutResult:"
            steps.echo rolloutResult

            def deploymentSnapshotAfter = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get deployment ${deploymentName} -n ${namespace} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "deploymentSnapshotAfter:"
            steps.echo deploymentSnapshotAfter

            def podsSnapshotAfter = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get pods -n ${namespace} -l app=${deploymentName} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "podsSnapshotAfter:"
            steps.echo podsSnapshotAfter

            def serviceSnapshotAfter = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get service ${serviceName} -n ${namespace} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "serviceSnapshotAfter:"
            steps.echo serviceSnapshotAfter
        }
    }
}