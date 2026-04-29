package org.styxcd.pipeline.stages.stagesimpl

class EKSWorkflowLoadBalancingController implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowLoadBalancingController(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'install AWS load balance controller'
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


        //installing aws lb controller
        steps.echo "in eks worfklow install lb conroller stage"

        steps.withCredentials([
                steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def awsRegion = 'us-east-1'
            def awsAccountId = '359546647832'
            def clusterName = 'johnny-johnny-dev'

            def policyName = 'AWSLoadBalancerControllerIAMPolicy'
            def policyArn = "arn:aws:iam::${awsAccountId}:policy/${policyName}"

            def serviceAccountName = 'aws-load-balancer-controller'
            def namespace = 'kube-system'

            // Later these come from YAML.
            def createPolicyIfMissing = true
            def policyDocumentPath = ''

            def identity = steps.sh(
                    script: 'aws sts get-caller-identity',
                    returnStdout: true
            ).trim()
            steps.echo "AWS Identity: ${identity}"

            def clusterStatus = steps.sh(
                    script: "eksctl get cluster --region ${awsRegion} --name ${clusterName}",
                    returnStatus: true
            )
            steps.echo "Cluster status before installing load balancer controller: ${clusterStatus}"

            if (clusterStatus != 0) {
                steps.error "Cluster does not exist. Cannot install AWS Load Balancer Controller."
            }

            def updateKubeconfigOutput = steps.sh(
                    script: "aws eks update-kubeconfig --region ${awsRegion} --name ${clusterName}",
                    returnStdout: true
            ).trim()
            steps.echo "Update kubeconfig output: ${updateKubeconfigOutput}"

            def oidcStatus = steps.sh(
                    script: "eksctl utils associate-iam-oidc-provider --region ${awsRegion} --cluster ${clusterName} --approve",
                    returnStatus: true
            )
            steps.echo "OIDC provider association status: ${oidcStatus}"

            if (oidcStatus != 0) {
                steps.error "OIDC provider association failed with status: ${oidcStatus}"
            }

            def policyStatus = steps.sh(
                    script: "aws iam get-policy --policy-arn ${policyArn}",
                    returnStatus: true
            )
            steps.echo "Load balancer controller IAM policy status: ${policyStatus}"

            if (policyStatus == 0) {
                steps.echo "IAM policy already exists: ${policyArn}"
            } else if (policyDocumentPath?.trim()) {
                steps.echo "Creating IAM policy from provided file: ${policyDocumentPath}"

                def createPolicyStatus = steps.sh(
                        script: "aws iam create-policy --policy-name ${policyName} --policy-document file://${policyDocumentPath}",
                        returnStatus: true
                )
                steps.echo "Create IAM policy status: ${createPolicyStatus}"

                if (createPolicyStatus != 0) {
                    steps.error "Failed to create IAM policy from provided file with status: ${createPolicyStatus}"
                }
            } else if (createPolicyIfMissing) {
                steps.echo "No policy file provided. Downloading default AWS Load Balancer Controller IAM policy."

                def downloadPolicyStatus = steps.sh(
                        script: 'curl -o iam_policy.json https://raw.githubusercontent.com/kubernetes-sigs/aws-load-balancer-controller/v2.14.1/docs/install/iam_policy.json',
                        returnStatus: true
                )
                steps.echo "Download IAM policy status: ${downloadPolicyStatus}"

                if (downloadPolicyStatus != 0) {
                    steps.error "Failed to download default IAM policy with status: ${downloadPolicyStatus}"
                }

                def createPolicyStatus = steps.sh(
                        script: "aws iam create-policy --policy-name ${policyName} --policy-document file://iam_policy.json",
                        returnStatus: true
                )
                steps.echo "Create IAM policy status: ${createPolicyStatus}"

                if (createPolicyStatus != 0) {
                    steps.error "Failed to create IAM policy from default file with status: ${createPolicyStatus}"
                }
            } else {
                steps.error "IAM policy does not exist and createPolicyIfMissing is false."
            }

            def serviceAccountStatus = steps.sh(
                    script: "kubectl get serviceaccount ${serviceAccountName} -n ${namespace}",
                    returnStatus: true
            )
            steps.echo "Load balancer controller service account status: ${serviceAccountStatus}"

            def createServiceAccountStatus = steps.sh(
                    script: "eksctl create iamserviceaccount --cluster ${clusterName} --namespace ${namespace} --name ${serviceAccountName} --attach-policy-arn ${policyArn} --approve --override-existing-serviceaccounts",
                    returnStatus: true
            )
            steps.echo "Create/update IAM service account status: ${createServiceAccountStatus}"

            if (createServiceAccountStatus != 0) {
                steps.error "Failed to create/update IAM service account with status: ${createServiceAccountStatus}"
            }

            def helmRepoStatus = steps.sh(
                    script: 'helm repo add eks https://aws.github.io/eks-charts',
                    returnStatus: true
            )
            steps.echo "Helm repo add status: ${helmRepoStatus}"

            def helmRepoUpdateStatus = steps.sh(
                    script: 'helm repo update',
                    returnStatus: true
            )
            steps.echo "Helm repo update status: ${helmRepoUpdateStatus}"

            if (helmRepoUpdateStatus != 0) {
                steps.error "Helm repo update failed with status: ${helmRepoUpdateStatus}"
            }

            def vpcId = steps.sh(
                    script: "aws eks describe-cluster --name ${clusterName} --region ${awsRegion} --query cluster.resourcesVpcConfig.vpcId --output text",
                    returnStdout: true
            ).trim()
            steps.echo "Cluster VPC ID: ${vpcId}"

            def helmInstallStatus = steps.sh(
                    script: "helm upgrade --install aws-load-balancer-controller eks/aws-load-balancer-controller -n ${namespace} --set clusterName=${clusterName} --set serviceAccount.create=false --set serviceAccount.name=${serviceAccountName} --set region=${awsRegion} --set vpcId=${vpcId}",
                    returnStatus: true
            )
            steps.echo "Helm install/upgrade status: ${helmInstallStatus}"

            if (helmInstallStatus != 0) {
                steps.error "AWS Load Balancer Controller Helm install/upgrade failed with status: ${helmInstallStatus}"
            }

            def rolloutStatus = steps.sh(
                    script: "kubectl rollout status deployment/aws-load-balancer-controller -n ${namespace}",
                    returnStatus: true
            )
            steps.echo "AWS Load Balancer Controller rollout status: ${rolloutStatus}"

            if (rolloutStatus != 0) {
                steps.error "AWS Load Balancer Controller rollout failed with status: ${rolloutStatus}"
            }

            def ingressClassOutput = steps.sh(
                    script: 'kubectl get ingressclass',
                    returnStdout: true
            ).trim()
            steps.echo "Ingress classes:\n${ingressClassOutput}"
        }




    }
}
