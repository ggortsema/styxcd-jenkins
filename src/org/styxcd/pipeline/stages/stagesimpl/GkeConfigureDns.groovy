package org.styxcd.pipeline.stages.stagesimpl

class GkeConfigureDns implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public GkeConfigureDns(steps, featureFlags) {
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
        yml.release?.environments?."${params ['LIFECYCLE']}"?.each { target ->
            if(target?.name == params ['TARGET_NAME']) {

                params['CLUSTER_NAME'] = target?.platform?.cluster_name
                params['PROJECT_ID'] = target?.platform?.project_id
                params['LOCATION'] = target?.platform?.location
                params['LOCATION_TYPE'] = target?.platform?.location_type
                params['NAMESPACE'] = target?.platform?.namespace
                params['CREDENTIALS_ID'] = target?.platform?.credentials?.id
                params['INGRESS_NAME'] = target?.platform?.ingress?.name
                params['DNS_ENABLED'] = target?.platform?.dns?.enabled
                params['DNS_PROVIDER'] = target?.platform?.dns?.provider
                params['DNS_HOSTED_ZONE'] = target?.platform?.dns?.hosted_zone
                params['DNS_RECORD_NAME'] = target?.platform?.dns?.record_name
                params['DNS_RECORD_TYPE'] = target?.platform?.dns?.record_type
                params['DNS_TTL'] = target?.platform?.dns?.ttl
                params['DNS_CREDENTIAL_SOURCE'] = target?.platform?.dns?.credentials?.source
                params['DNS_ACCESS_KEY_ID_CREDENTIAL'] = target?.platform?.dns?.credentials?.access_key_id
                params['DNS_SECRET_ACCESS_KEY_CREDENTIAL'] = target?.platform?.dns?.credentials?.secret_access_key
            }
        }

        def clusterName = params['CLUSTER_NAME']
        def projectId = params['PROJECT_ID']
        def location = params['LOCATION']
        def locationType = params['LOCATION_TYPE']
        def namespace = params['NAMESPACE']
        def credentialsId = params['CREDENTIALS_ID']
        def locationFlag = locationType == 'regional' ? '--region' : '--zone'
        def ingressName = params['INGRESS_NAME']
        def dnsEnabled = params['DNS_ENABLED']
        def dnsProvider = params['DNS_PROVIDER']
        def dnsHostedZone = params['DNS_HOSTED_ZONE']
        def dnsRecordName = params['DNS_RECORD_NAME']
        def dnsRecordType = params['DNS_RECORD_TYPE']
        def dnsTtl = params['DNS_TTL']
        def dnsCredentialSource = params['DNS_CREDENTIAL_SOURCE']
        def dnsAccessKeyId = params['DNS_ACCESS_KEY_ID_CREDENTIAL']
        def dnsSecretAccessKey = params['DNS_SECRET_ACCESS_KEY_CREDENTIAL']

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

        def ingressAddress = steps.sh(
                script: """
            kubectl --kubeconfig=${kubeConfig} \
            get ingress ${ingressName} \
            -n ${namespace} \
            -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
        """.stripIndent(),
                returnStdout: true
        ).trim()

        if(!ingressAddress) {
            steps.error("Ingress address not available for ${ingressName}")
        }

        params['INGRESS_ADDRESS'] = ingressAddress
        stageSpecificMap['INGRESS_ADDRESS'] = ingressAddress

        steps.echo("ingressAddress: ${ingressAddress}")

        steps.withCredentials([
                steps.string(credentialsId: dnsAccessKeyId, variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: dnsSecretAccessKey, variable: 'AWS_SECRET_ACCESS_KEY')
        ]) {
            def hostedZoneId = steps.sh(
                    script: """
            AWS_ACCESS_KEY_ID="\$AWS_ACCESS_KEY_ID" \
            AWS_SECRET_ACCESS_KEY="\$AWS_SECRET_ACCESS_KEY" \
            aws route53 list-hosted-zones-by-name \
              --dns-name ${dnsHostedZone} \
              --query "HostedZones[0].Id" \
              --output text
        """.stripIndent(),
                    returnStdout: true
            ).trim().replace('/hostedzone/', '')

            def changeBatchFile = "${steps.env.WORKSPACE}/${dnsRecordName}-route53-upsert.json"

            steps.writeFile(
                    file: changeBatchFile,
                    text: """
{
  "Comment": "StyxCD UPSERT for ${dnsRecordName}",
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${dnsRecordName}",
        "Type": "${dnsRecordType}",
        "TTL": ${dnsTtl},
        "ResourceRecords": [
          {
            "Value": "${ingressAddress}"
          }
        ]
      }
    }
  ]
}
""".stripIndent()
            )
            def dnsResult = steps.sh(
                    script: """
            AWS_ACCESS_KEY_ID="\$AWS_ACCESS_KEY_ID" \
            AWS_SECRET_ACCESS_KEY="\$AWS_SECRET_ACCESS_KEY" \
            aws route53 change-resource-record-sets \
              --hosted-zone-id ${hostedZoneId} \
              --change-batch file://${changeBatchFile}
        """.stripIndent(),
                    returnStdout: true
            ).trim()

            steps.echo("dnsResult:")
            steps.echo(dnsResult)

        }

    }
}
