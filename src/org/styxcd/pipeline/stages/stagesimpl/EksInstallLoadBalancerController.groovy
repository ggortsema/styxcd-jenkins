package org.styxcd.pipeline.stages.stagesimpl

class EksInstallLoadBalancerController implements Serializable {

    def steps

    public EksInstallLoadBalancerController(steps, featureFlags) {
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

        // TODO remove this parsing bridge later and get values directly from orchestrator
        yml.release?.environments?."${params['LIFECYCLE']}"?.each { target ->
            if (target?.name == params['TARGET_NAME'] && target?.platform?.name == 'eks') {
                params['AWS_REGION'] = target?.platform?.region
                params['CLUSTER_NAME'] = target?.platform?.cluster_name
                params['NAMESPACE'] = target?.platform?.namespace
                params['AWS_ACCESS_KEY_ID_CREDENTIAL'] = target?.platform?.credentials?.access_key_id
                params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] = target?.platform?.credentials?.secret_access_key
            }
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        if (!awsRegion?.trim()) {
            steps.error "Missing AWS_REGION for EKS target ${params['TARGET_NAME']}"
        }

        if (!clusterName?.trim()) {
            steps.error "Missing CLUSTER_NAME for EKS target ${params['TARGET_NAME']}"
        }

        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"
        def helmConfigHome = "${steps.env.WORKSPACE}/.helm/config"
        def helmCacheHome = "${steps.env.WORKSPACE}/.helm/cache"
        def helmDataHome = "${steps.env.WORKSPACE}/.helm/data"

        steps.sh(script: "mkdir -p ${steps.env.WORKSPACE}/.kube", returnStdout: true).trim()
        steps.sh(script: "mkdir -p ${helmConfigHome} ${helmCacheHome} ${helmDataHome}", returnStdout: true).trim()

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

            // TEMPORARY MVP WORKAROUND:
            // For the current YAML-driven EKS workflow, grant the existing node role broad ELB permissions.
            // Future backlog item: replace this with proper IRSA for aws-load-balancer-controller.
            def firstNodeProviderId = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get nodes -o jsonpath='{.items[0].spec.providerID}'",
                    returnStdout: true
            ).trim()

            steps.echo "firstNodeProviderId:"
            steps.echo firstNodeProviderId

            def instanceId = firstNodeProviderId.tokenize('/').last()

            steps.echo "instanceId:"
            steps.echo instanceId

            def instanceProfileArn = steps.sh(
                    script: "aws ec2 describe-instances --region ${awsRegion} --instance-ids ${instanceId} --query \"Reservations[0].Instances[0].IamInstanceProfile.Arn\" --output text",
                    returnStdout: true
            ).trim()

            steps.echo "instanceProfileArn:"
            steps.echo instanceProfileArn

            def instanceProfileName = instanceProfileArn.tokenize('/').last()

            steps.echo "instanceProfileName:"
            steps.echo instanceProfileName

            def nodeRoleName = steps.sh(
                    script: "aws iam get-instance-profile --instance-profile-name ${instanceProfileName} --query \"InstanceProfile.Roles[0].RoleName\" --output text",
                    returnStdout: true
            ).trim()

            steps.echo "nodeRoleName:"
            steps.echo nodeRoleName

            def attachElbPolicyResult = steps.sh(
                    script: "aws iam attach-role-policy --role-name ${nodeRoleName} --policy-arn arn:aws:iam::aws:policy/ElasticLoadBalancingFullAccess",
                    returnStatus: true
            )

            steps.echo "attachElbPolicyResult: ${attachElbPolicyResult}"

            def helmEnv = "KUBECONFIG=${kubeConfig} HELM_CONFIG_HOME=${helmConfigHome} HELM_CACHE_HOME=${helmCacheHome} HELM_DATA_HOME=${helmDataHome}"

            def helmRepoAddResult = steps.sh(
                    script: "${helmEnv} helm repo add eks https://aws.github.io/eks-charts || true",
                    returnStdout: true
            ).trim()

            steps.echo "helmRepoAddResult:"
            steps.echo helmRepoAddResult

            def helmRepoUpdateResult = steps.sh(
                    script: "${helmEnv} helm repo update eks",
                    returnStdout: true
            ).trim()

            steps.echo "helmRepoUpdateResult:"
            steps.echo helmRepoUpdateResult

            def installResult = steps.sh(
                    script: """
${helmEnv} helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller \
  -n kube-system \
  --set clusterName=${clusterName} \
  --set region=${awsRegion} \
  --set vpcId=\$(aws eks describe-cluster --region ${awsRegion} --name ${clusterName} --query "cluster.resourcesVpcConfig.vpcId" --output text)
""".stripIndent(),
                    returnStdout: true
            ).trim()

            steps.echo "installResult:"
            steps.echo installResult

            def serviceAccountResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get serviceaccount aws-load-balancer-controller -n kube-system -o yaml",
                    returnStdout: true
            ).trim()

            steps.echo "serviceAccountResult:"
            steps.echo serviceAccountResult

            def controllerDeploymentResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get deployment aws-load-balancer-controller -n kube-system -o yaml",
                    returnStdout: true
            ).trim()

            steps.echo "controllerDeploymentResult:"
            steps.echo controllerDeploymentResult

            def controllerLogsResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} logs deployment/aws-load-balancer-controller -n kube-system --tail=50",
                    returnStdout: true
            ).trim()

            steps.echo "controllerLogsResult:"
            steps.echo controllerLogsResult

            def rolloutRestartResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} rollout restart deployment/aws-load-balancer-controller -n kube-system",
                    returnStdout: true
            ).trim()

            steps.echo "rolloutRestartResult:"
            steps.echo rolloutRestartResult

            def rolloutStatus = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} rollout status deployment/aws-load-balancer-controller -n kube-system --timeout=300s",
                    returnStatus: true
            )

            steps.echo "Load balancer controller rollout status: ${rolloutStatus}"

            if (rolloutStatus != 0) {
                steps.error "AWS Load Balancer Controller rollout failed with status: ${rolloutStatus}"
            }

            def ingressClassResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get ingressclass alb",
                    returnStdout: true
            ).trim()

            steps.echo "ingressClassResult:"
            steps.echo ingressClassResult
        }
    }
}