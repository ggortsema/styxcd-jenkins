package org.styxcd.pipeline.stages.stagesimpl

class EKSWorkflowDeployImages implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowDeployImages(steps, featureFlags) {
        this.steps = steps
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'deploy images to EKS'
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


        steps.echo "in deploy images to EKS stage"

        steps.withCredentials([
                steps.string(credentialsId: 'aws-access-key-id', variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: 'aws-secret-access-key', variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def awsRegion = 'us-east-1'
            def clusterName = 'johnny-johnny-dev'
            def namespace = 'default'

            def deploymentRepo = 'https://github.com/ggortsema/johnny-johnny-deployment.git'
            def deploymentDir = 'johnny-johnny-deployment'
            def manifestDir = "${deploymentDir}/eks/dev"

            def frontendName = 'johnny-frontend'
            def backendName = 'johnny-backend'
            def ingressName = 'johnny-ingress'

            def rootDomain = 'mycroftai.org.'
            def frontendHost = 'johnny-johnny.mycroftai.org'
            def backendHost = 'api.johnny-johnny.mycroftai.org'

            def identity = steps.sh(
                    script: 'aws sts get-caller-identity',
                    returnStdout: true
            ).trim()
            steps.echo "AWS Identity: ${identity}"

            def updateKubeconfigOutput = steps.sh(
                    script: "aws eks update-kubeconfig --region ${awsRegion} --name ${clusterName}",
                    returnStdout: true
            ).trim()
            steps.echo "Update kubeconfig output: ${updateKubeconfigOutput}"

            def albIngressClassStatus = steps.sh(
                    script: 'kubectl get ingressclass alb',
                    returnStatus: true
            )
            steps.echo "ALB ingress class status before app deploy: ${albIngressClassStatus}"

            if (albIngressClassStatus != 0) {
                steps.error "ALB ingress class does not exist. Cannot deploy apps."
            }

            def openAiSecretStatus = steps.sh(
                    script: "kubectl get secret openai-secret -n ${namespace}",
                    returnStatus: true
            )
            steps.echo "OpenAI secret status: ${openAiSecretStatus}"

            if (openAiSecretStatus != 0) {
                steps.error "Missing Kubernetes secret openai-secret in namespace ${namespace}."
            }

            def removeExistingRepoStatus = steps.sh(
                    script: "rm -rf ${deploymentDir}",
                    returnStatus: true
            )
            steps.echo "Remove existing deployment repo status: ${removeExistingRepoStatus}"

            def cloneStatus = steps.sh(
                    script: "git clone ${deploymentRepo}",
                    returnStatus: true
            )
            steps.echo "Deployment repo clone status: ${cloneStatus}"

            if (cloneStatus != 0) {
                steps.error "Failed to clone deployment repo with status: ${cloneStatus}"
            }

            def backendApplyStatus = steps.sh(
                    script: "kubectl apply -n ${namespace} -f ${manifestDir}/backend.yml",
                    returnStatus: true
            )
            steps.echo "Backend apply status: ${backendApplyStatus}"

            if (backendApplyStatus != 0) {
                steps.error "Failed to apply backend manifest with status: ${backendApplyStatus}"
            }

            def frontendApplyStatus = steps.sh(
                    script: "kubectl apply -n ${namespace} -f ${manifestDir}/frontend.yml",
                    returnStatus: true
            )
            steps.echo "Frontend apply status: ${frontendApplyStatus}"

            if (frontendApplyStatus != 0) {
                steps.error "Failed to apply frontend manifest with status: ${frontendApplyStatus}"
            }

            def backendRolloutStatus = steps.sh(
                    script: "kubectl rollout status deployment/${backendName} -n ${namespace}",
                    returnStatus: true
            )
            steps.echo "Backend rollout status: ${backendRolloutStatus}"

            if (backendRolloutStatus != 0) {
                steps.error "Backend rollout failed with status: ${backendRolloutStatus}"
            }

            def frontendRolloutStatus = steps.sh(
                    script: "kubectl rollout status deployment/${frontendName} -n ${namespace}",
                    returnStatus: true
            )
            steps.echo "Frontend rollout status: ${frontendRolloutStatus}"

            if (frontendRolloutStatus != 0) {
                steps.error "Frontend rollout failed with status: ${frontendRolloutStatus}"
            }

            def ingressApplyStatus = steps.sh(
                    script: "kubectl apply -n ${namespace} -f ${manifestDir}/ingress.yml",
                    returnStatus: true
            )
            steps.echo "Ingress apply status: ${ingressApplyStatus}"

            if (ingressApplyStatus != 0) {
                steps.error "Failed to apply ingress manifest with status: ${ingressApplyStatus}"
            }

            def waitForAlbStatus = steps.sh(
                    script: "kubectl wait -n ${namespace} --for=jsonpath='{.status.loadBalancer.ingress[0].hostname}' ingress/${ingressName} --timeout=300s",
                    returnStatus: true
            )
            steps.echo "Wait for ALB hostname status: ${waitForAlbStatus}"

            if (waitForAlbStatus != 0) {
                steps.error "Timed out waiting for ALB hostname."
            }

            def albHostname = steps.sh(
                    script: "kubectl get ingress ${ingressName} -n ${namespace} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'",
                    returnStdout: true
            ).trim()
            steps.echo "ALB hostname: ${albHostname}"

            if (!albHostname?.trim()) {
                steps.error "ALB hostname was empty."
            }

            def hostedZoneId = steps.sh(
                    script: "aws route53 list-hosted-zones-by-name --dns-name ${rootDomain} --query \"HostedZones[0].Id\" --output text",
                    returnStdout: true
            ).trim().replace('/hostedzone/', '')
            steps.echo "Hosted zone ID: ${hostedZoneId}"

            if (!hostedZoneId?.trim() || hostedZoneId == 'None') {
                steps.error "Could not find Route53 hosted zone for ${rootDomain}"
            }

            steps.writeFile(
                    file: 'route53-change.json',
                    text: """
{
  "Comment": "UPSERT johnny-johnny frontend and backend records to EKS ALB",
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${frontendHost}",
        "Type": "CNAME",
        "TTL": 60,
        "ResourceRecords": [
          {
            "Value": "${albHostname}"
          }
        ]
      }
    },
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${backendHost}",
        "Type": "CNAME",
        "TTL": 60,
        "ResourceRecords": [
          {
            "Value": "${albHostname}"
          }
        ]
      }
    }
  ]
}
""".stripIndent()
            )

            def route53Status = steps.sh(
                    script: "aws route53 change-resource-record-sets --hosted-zone-id ${hostedZoneId} --change-batch file://route53-change.json",
                    returnStatus: true
            )
            steps.echo "Route53 UPSERT status: ${route53Status}"

            if (route53Status != 0) {
                steps.error "Route53 UPSERT failed with status: ${route53Status}"
            }

            def podsOutput = steps.sh(
                    script: "kubectl get pods -n ${namespace}",
                    returnStdout: true
            ).trim()
            steps.echo "Pods:\n${podsOutput}"

            def servicesOutput = steps.sh(
                    script: "kubectl get services -n ${namespace}",
                    returnStdout: true
            ).trim()
            steps.echo "Services:\n${servicesOutput}"

            def ingressOutput = steps.sh(
                    script: "kubectl get ingress ${ingressName} -n ${namespace}",
                    returnStdout: true
            ).trim()
            steps.echo "Ingress:\n${ingressOutput}"

            steps.echo "Apps deployed. Frontend: http://${frontendHost} Backend: http://${backendHost}"
        }




    }
}
