package org.styxcd.pipeline.stages.stagesimpl

class EksDeployApplication implements Serializable {

    def steps

    public EksDeployApplication(steps, featureFlags) {
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

                def targetApp = target?.platform?.applications?.find { it?.name == params['APP_NAME'] }
                def releaseApp = yml.release?.applications?.values()?.flatten()?.find { it?.name == params['APP_NAME'] }
                def dockerArtifact = releaseApp?.artifacts?.find { it?.type == 'docker-image' }

                params['IMAGE'] = dockerArtifact?.image
                params['REPLICAS'] = targetApp?.replicas ?: target?.platform?.defaults?.replicas
                params['SERVICE_TYPE'] = targetApp?.service?.type ?: target?.platform?.defaults?.service?.type
                params['CONTAINER_PORT'] = targetApp?.container?.port
                params['SERVICE_PORT'] = targetApp?.service?.port
                params['SERVICE_TARGET_PORT'] = targetApp?.service?.target_port
                params['DEPLOYMENT_NAME'] = params['APP_NAME']
                params['SERVICE_NAME'] = params['APP_NAME']
                params['TARGET_APP'] = targetApp
            }
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def namespace = params['NAMESPACE'] ?: 'default'
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        def appName = params['APP_NAME']
        def image = params['IMAGE']
        def replicas = params['REPLICAS'] ?: 1
        def serviceType = params['SERVICE_TYPE'] ?: 'ClusterIP'
        def deploymentName = params['DEPLOYMENT_NAME']
        def serviceName = params['SERVICE_NAME']
        def containerPort = params['CONTAINER_PORT']
        def servicePort = params['SERVICE_PORT']
        def serviceTargetPort = params['SERVICE_TARGET_PORT'] ?: containerPort
        def targetApp = params['TARGET_APP']

        if (!appName?.trim()) {
            steps.error "Missing APP_NAME"
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

        if (!image?.trim()) {
            steps.error "Missing docker-image artifact for APP_NAME ${appName}"
        }

        if (!containerPort) {
            steps.error "Missing CONTAINER_PORT for APP_NAME ${appName}"
        }

        if (!servicePort) {
            steps.error "Missing SERVICE_PORT for APP_NAME ${appName}"
        }

        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"

        steps.sh(script: "mkdir -p ${steps.env.WORKSPACE}/.kube", returnStdout: true).trim()

        def credentialBindings = [
                steps.string(credentialsId: awsAccessKeyCredential, variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: awsSecretKeyCredential, variable: 'AWS_SECRET_ACCESS_KEY')
        ]

        targetApp?.secrets?.each { secretConfig ->
            credentialBindings.add(
                    steps.string(
                            credentialsId: secretConfig?.source?.credential_id,
                            variable: secretConfig?.env_name
                    )
            )
        }

        steps.withCredentials(credentialBindings) {

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

            def namespaceResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get namespace ${namespace}",
                    returnStdout: true
            ).trim()

            steps.echo "namespaceResult:"
            steps.echo namespaceResult

            targetApp?.secrets?.each { secretConfig ->
                def envName = secretConfig?.env_name
                def secretName = "${appName}-${envName.toLowerCase().replaceAll('_', '-')}".toString()

                def secretApplyResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} create secret generic ${secretName} -n ${namespace} --from-literal=${envName}=\$${envName} --dry-run=client -o yaml | kubectl --kubeconfig=${kubeConfig} apply -f -",
                        returnStdout: true
                ).trim()

                steps.echo "secretApplyResult for ${secretName}:"
                steps.echo secretApplyResult
            }

            def envBlock = ""

            targetApp?.secrets?.each { secretConfig ->
                def envName = secretConfig?.env_name
                def secretName = "${appName}-${envName.toLowerCase().replaceAll('_', '-')}".toString()

                envBlock += """
            - name: ${envName}
              valueFrom:
                secretKeyRef:
                  name: ${secretName}
                  key: ${envName}
"""
            }

            targetApp?.env?.each { envConfig ->
                envBlock += """
            - name: ${envConfig?.name}
              value: "${envConfig?.value}"
"""
            }

            def envYaml = ""

            if (envBlock?.trim()) {
                envYaml = """
          env:
${envBlock}
"""
            }

            steps.writeFile(
                    file: "${deploymentName}-deployment.yml",
                    text: """
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${deploymentName}
  namespace: ${namespace}
  labels:
    app: ${deploymentName}
spec:
  replicas: ${replicas}
  selector:
    matchLabels:
      app: ${deploymentName}
  template:
    metadata:
      labels:
        app: ${deploymentName}
    spec:
      containers:
        - name: ${deploymentName}
          image: ${image}
          imagePullPolicy: Always
          ports:
            - containerPort: ${containerPort}
${envYaml}
""".stripIndent()
            )

            steps.writeFile(
                    file: "${serviceName}-service.yml",
                    text: """
apiVersion: v1
kind: Service
metadata:
  name: ${serviceName}
  namespace: ${namespace}
  labels:
    app: ${deploymentName}
spec:
  type: ${serviceType}
  selector:
    app: ${deploymentName}
  ports:
    - port: ${servicePort}
      targetPort: ${serviceTargetPort}
      protocol: TCP
""".stripIndent()
            )

            def deploymentApplyResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} apply -f ${deploymentName}-deployment.yml",
                    returnStdout: true
            ).trim()

            steps.echo "deploymentApplyResult:"
            steps.echo deploymentApplyResult

            def serviceApplyResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} apply -f ${serviceName}-service.yml",
                    returnStdout: true
            ).trim()

            steps.echo "serviceApplyResult:"
            steps.echo serviceApplyResult

            def deploymentResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get deployment ${deploymentName} -n ${namespace}",
                    returnStdout: true
            ).trim()

            steps.echo "deploymentResult:"
            steps.echo deploymentResult

            def serviceResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get service ${serviceName} -n ${namespace}",
                    returnStdout: true
            ).trim()

            steps.echo "serviceResult:"
            steps.echo serviceResult
        }
    }
}