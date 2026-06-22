package org.styxcd.pipeline.stages.stagesimpl

class EksConfigureDns implements Serializable {

    def steps

    public EksConfigureDns(steps, featureFlags) {
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

                params['INGRESS_NAME'] = target?.platform?.ingress?.name

                params['DNS_ENABLED'] = target?.platform?.dns?.enabled
                params['DNS_PROVIDER'] = target?.platform?.dns?.provider
                params['DNS_HOSTED_ZONE'] = target?.platform?.dns?.hosted_zone
                params['DNS_RECORD_TYPE'] = target?.platform?.dns?.record_type
                params['DNS_TTL'] = target?.platform?.dns?.ttl
                params['DNS_CREDENTIAL_SOURCE'] = target?.platform?.dns?.credentials?.source
                params['DNS_ACCESS_KEY_ID_CREDENTIAL'] = target?.platform?.dns?.credentials?.access_key_id
                params['DNS_SECRET_ACCESS_KEY_CREDENTIAL'] = target?.platform?.dns?.credentials?.secret_access_key

                def dnsRecordsList = []

                target?.platform?.ingress?.hosts?.each { hostRule ->
                    dnsRecordsList << [
                            name: hostRule?.host,
                            type: target?.platform?.dns?.record_type,
                            ttl : target?.platform?.dns?.ttl
                    ]
                }

                params['DNS_RECORDS'] = dnsRecordsList
            }
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def namespace = params['NAMESPACE'] ?: 'default'
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        def ingressName = params['INGRESS_NAME']

        def dnsEnabled = params['DNS_ENABLED']
        def dnsProvider = params['DNS_PROVIDER']
        def dnsHostedZone = params['DNS_HOSTED_ZONE']
        def dnsRecords = params['DNS_RECORDS']
        def dnsAccessKeyId = params['DNS_ACCESS_KEY_ID_CREDENTIAL'] ?: awsAccessKeyCredential
        def dnsSecretAccessKey = params['DNS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: awsSecretKeyCredential

        if (dnsEnabled == false) {
            steps.echo "DNS is disabled for EKS target ${params['TARGET_NAME']}. Skipping."
            return
        }

        if (!awsRegion?.trim()) {
            steps.error "Missing AWS_REGION for EKS target ${params['TARGET_NAME']}"
        }

        if (!clusterName?.trim()) {
            steps.error "Missing CLUSTER_NAME for EKS target ${params['TARGET_NAME']}"
        }

        if (!namespace?.trim()) {
            steps.error "Missing NAMESPACE for EKS target ${params['TARGET_NAME']}"
        }

        if (!ingressName?.trim()) {
            steps.error "Missing INGRESS_NAME for EKS target ${params['TARGET_NAME']}"
        }

        if (!dnsProvider?.trim()) {
            steps.error "Missing DNS_PROVIDER for EKS target ${params['TARGET_NAME']}"
        }

        if (dnsProvider != 'route53') {
            steps.error "Unsupported DNS provider for EKS: ${dnsProvider}"
        }

        if (!dnsHostedZone?.trim()) {
            steps.error "Missing DNS_HOSTED_ZONE for EKS target ${params['TARGET_NAME']}"
        }

        if (!dnsRecords || dnsRecords.isEmpty()) {
            steps.error "No DNS records found for ingress ${ingressName}"
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

            def waitForAlbStatus = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} wait -n ${namespace} --for=jsonpath='{.status.loadBalancer.ingress[0].hostname}' ingress/${ingressName} --timeout=600s",
                    returnStatus: true
            )

            steps.echo "waitForAlbStatus: ${waitForAlbStatus}"

            if (waitForAlbStatus != 0) {
                def ingressDescribeResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} describe ingress ${ingressName} -n ${namespace}",
                        returnStdout: true
                ).trim()

                steps.echo "ingressDescribeResult:"
                steps.echo ingressDescribeResult

                steps.error "Timed out waiting for ALB hostname for ingress ${ingressName}"
            }

            def albHostname = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get ingress ${ingressName} -n ${namespace} -o jsonpath='{.status.loadBalancer.ingress[0].hostname}'",
                    returnStdout: true
            ).trim()

            steps.echo "albHostname:"
            steps.echo albHostname

            if (!albHostname?.trim()) {
                steps.error "ALB hostname was empty for ingress ${ingressName}"
            }

            params['INGRESS_ADDRESS'] = albHostname
            stageSpecificMap['INGRESS_ADDRESS'] = albHostname

            def ingressResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get ingress ${ingressName} -n ${namespace} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "ingressResult:"
            steps.echo ingressResult
        }

        steps.withCredentials([
                steps.string(credentialsId: dnsAccessKeyId, variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: dnsSecretAccessKey, variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {

            def hostedZoneId = steps.sh(
                    script: """
aws route53 list-hosted-zones-by-name \
  --dns-name ${dnsHostedZone} \
  --query "HostedZones[0].Id" \
  --output text
""".stripIndent(),
                    returnStdout: true
            ).trim().replace('/hostedzone/', '')

            steps.echo "hostedZoneId:"
            steps.echo hostedZoneId

            if (!hostedZoneId?.trim() || hostedZoneId == 'None') {
                steps.error "Could not find Route53 hosted zone for ${dnsHostedZone}"
            }

            def albHostname = stageSpecificMap['INGRESS_ADDRESS']

            def albCanonicalHostedZoneId = steps.sh(
                    script: """
aws elbv2 describe-load-balancers \
  --region ${awsRegion} \
  --query "LoadBalancers[?DNSName=='${albHostname}'].CanonicalHostedZoneId" \
  --output text
""".stripIndent(),
                    returnStdout: true
            ).trim()

            steps.echo "albCanonicalHostedZoneId:"
            steps.echo albCanonicalHostedZoneId

            if (!albCanonicalHostedZoneId?.trim()) {
                steps.error "Could not find ALB canonical hosted zone ID for ${albHostname}"
            }

            def changesBlock = dnsRecords.collect { record -> """
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${record.name}",
        "Type": "${record.type}",
        "AliasTarget": {
          "HostedZoneId": "${albCanonicalHostedZoneId}",
          "DNSName": "${albHostname}",
          "EvaluateTargetHealth": false
        }
      }
    }
""" }.join(',')

            def changeBatchFile = "${steps.env.WORKSPACE}/${ingressName}-route53-upsert.json"

            steps.writeFile(
                    file: changeBatchFile,
                    text: """
{
  "Comment": "StyxCD UPSERT for EKS ingress ${ingressName}",
  "Changes": [
${changesBlock}
  ]
}
""".stripIndent()
            )

            steps.echo "Route53 change batch:"
            steps.echo steps.readFile(changeBatchFile)

            def dnsResult = steps.sh(
                    script: """
aws route53 change-resource-record-sets \
  --hosted-zone-id ${hostedZoneId} \
  --change-batch file://${changeBatchFile}
""".stripIndent(),
                    returnStdout: true
            ).trim()

            steps.echo "dnsResult:"
            steps.echo dnsResult
        }
    }
}