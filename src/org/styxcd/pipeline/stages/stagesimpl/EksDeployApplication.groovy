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

        def releaseApplicationConfig = yml.release?.applications?.values()?.flatten()?.find {
            it?.name == applicationName
        }

        def targetApplicationConfig = targetConfig?.platform?.applications?.find {
            it?.name == applicationName
        }

        if (releaseApplicationConfig == null) {
            steps.error "Could not find release application config for APPLICATION_NAME ${applicationName}"
        }

        if (targetApplicationConfig == null) {
            steps.error "Could not find target application config for APPLICATION_NAME ${applicationName}"
        }

        def dockerArtifact = releaseApplicationConfig?.artifacts?.find {
            it?.type == 'docker-image'
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def namespace = params['NAMESPACE'] ?: 'default'
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        def deploymentName = applicationName
        def serviceName = applicationName
        def image = dockerArtifact?.image
        def replicas = targetApplicationConfig?.replicas ?: targetConfig?.platform?.defaults?.replicas ?: 1
        def serviceType = targetApplicationConfig?.service?.type ?: targetConfig?.platform?.defaults?.service?.type ?: 'ClusterIP'
        def containerPort = targetApplicationConfig?.container?.port
        def servicePort = targetApplicationConfig?.service?.port
        def serviceTargetPort = targetApplicationConfig?.service?.target_port ?: containerPort

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
            steps.error "Missing docker-image artifact for application ${applicationName}"
        }

        if (!containerPort) {
            steps.error "Missing container.port for application ${applicationName}"
        }

        if (!servicePort) {
            steps.error "Missing service.port for application ${applicationName}"
        }

        def kubeConfig = "${steps.env.WORKSPACE}/.kube/config"

        steps.sh(script: "mkdir -p ${steps.env.WORKSPACE}/.kube", returnStdout: true).trim()

        def credentialBindings = [
                steps.string(credentialsId: awsAccessKeyCredential, variable: 'AWS_ACCESS_KEY_ID'),
                steps.string(credentialsId: awsSecretKeyCredential, variable: 'AWS_SECRET_ACCESS_KEY')
        ]

        targetApplicationConfig?.secrets?.each { secretConfig ->
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

            targetApplicationConfig?.secrets?.each { secretConfig ->
                def envName = secretConfig?.env_name
                def secretName = "${applicationName}-${envName.toLowerCase().replace('_', '-')}".toString()

                def secretApplyResult = steps.sh(
                        script: "kubectl --kubeconfig=${kubeConfig} create secret generic ${secretName} -n ${namespace} --from-literal=${envName}=\$${envName} --dry-run=client -o yaml | kubectl --kubeconfig=${kubeConfig} apply -f -",
                        returnStdout: true
                ).trim()

                steps.echo "secretApplyResult for ${secretName}:"
                steps.echo secretApplyResult
            }

            def envBlock = ""

            targetApplicationConfig?.secrets?.each { secretConfig ->
                def envName = secretConfig?.env_name
                def secretName = "${applicationName}-${envName.toLowerCase().replace('_', '-')}".toString()

                envBlock += """
            - name: ${envName}
              valueFrom:
                secretKeyRef:
                  name: ${secretName}
                  key: ${envName}
"""
            }

            targetApplicationConfig?.env?.each { envConfig ->
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