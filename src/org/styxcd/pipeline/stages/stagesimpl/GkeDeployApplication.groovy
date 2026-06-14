package org.styxcd.pipeline.stages.stagesimpl

class GkeDeployApplication implements Serializable {

    def steps

    public GkeDeployApplication(steps, featureFlags) {
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
            if (target?.name == params['TARGET_NAME']) {

                params['CLUSTER_NAME'] = target?.platform?.cluster_name
                params['PROJECT_ID'] = target?.platform?.project_id
                params['LOCATION'] = target?.platform?.location
                params['LOCATION_TYPE'] = target?.platform?.location_type
                params['NAMESPACE'] = target?.platform?.namespace
                params['CREDENTIALS_ID'] = target?.platform?.credentials?.id

                def targetApp = target?.platform?.applications?.find { it?.name == params['APP_NAME'] }
                def releaseApp = yml.release?.applications?.values()?.flatten()?.find { it?.name == params['APP_NAME'] }
                def dockerArtifact = releaseApp?.artifacts?.find { it?.type == 'docker-image' }

                params['IMAGE'] = dockerArtifact?.image
                params['REPLICAS'] = targetApp?.replicas ?: target?.platform?.defaults?.replicas
                params['SERVICE_TYPE'] = targetApp?.service?.type ?: target?.platform?.defaults?.service?.type

                def secretsMap = [:]
                targetApp?.secrets?.each { secret ->
                    def envName = secret?.env_name
                    secretsMap[envName] = [
                            sourceType  : secret?.source?.type,
                            credentialId: secret?.source?.credential_id
                    ]
                }
                params['SECRETS'] = secretsMap

                def envVarsList = []
                targetApp?.env?.each { envVar ->
                    envVarsList << [
                            name : envVar?.name,
                            value: envVar?.value
                    ]
                }
                params['ENV_VARS'] = envVarsList

                def healthCheck = targetApp?.health_check
                if (healthCheck) {
                    params['HEALTH_CHECK_ENABLED'] = true
                    params['HEALTH_CHECK_PATH'] = healthCheck?.path
                    params['HEALTH_CHECK_PORT'] = healthCheck?.port
                    params['HEALTH_CHECK_TYPE'] = healthCheck?.type ?: 'HTTP'
                    params['BACKEND_CONFIG_NAME'] = (params['APP_NAME'].toString() + "-backend-config")
                } else {
                    params['HEALTH_CHECK_ENABLED'] = false
                    params['BACKEND_CONFIG_NAME'] = null
                }

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
        def secrets = params['SECRETS']
        def envVars = params['ENV_VARS']

        def healthCheckEnabled = params['HEALTH_CHECK_ENABLED']
        def healthCheckPath = params['HEALTH_CHECK_PATH']
        def healthCheckPort = params['HEALTH_CHECK_PORT']
        def healthCheckType = params['HEALTH_CHECK_TYPE']
        def backendConfigName = params['BACKEND_CONFIG_NAME']

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

        def secretName = "${deploymentName}-secrets"

        if (secrets && !secrets.isEmpty()) {
            secrets.each { envName, secretConfig ->
                steps.withCredentials([steps.string(credentialsId: secretConfig.credentialId, variable: 'SECRET_VALUE')]) {
                    def secretApplyResult = steps.sh(
                            script: """
kubectl --kubeconfig=${kubeConfig} create secret generic ${secretName} \\
  -n ${namespace} \\
  --from-literal=${envName}="\$SECRET_VALUE" \\
  --dry-run=client -o yaml | kubectl --kubeconfig=${kubeConfig} apply -f -
""",
                            returnStdout: true
                    ).trim()

                    steps.echo("secretApplyResult for ${envName}:")
                    steps.echo(secretApplyResult)
                }
            }
        }

        def envEntries = []

        if (envVars && !envVars.isEmpty()) {
            envVars.each { envVar ->
                envEntries << """            - name: ${envVar.name}
              value: "${envVar.value}" """
            }
        }

        if (secrets && !secrets.isEmpty()) {
            secrets.each { envName, secretConfig ->
                envEntries << """            - name: ${envName}
              valueFrom:
                secretKeyRef:
                  name: ${secretName}
                  key: ${envName}"""
            }
        }

        def envBlock = ""

        if (!envEntries.isEmpty()) {
            envBlock = """
          env:
${envEntries.join('\n')}
"""
        }

        def serviceAnnotationsBlock = ""

        if (healthCheckEnabled) {
            serviceAnnotationsBlock = """
  annotations:
    cloud.google.com/backend-config: '{"default":"${backendConfigName}"}'
"""
        }

        def backendConfigBlock = ""

        if (healthCheckEnabled) {
            backendConfigBlock = """
---
apiVersion: cloud.google.com/v1
kind: BackendConfig
metadata:
  name: ${backendConfigName}
  namespace: ${namespace}
spec:
  healthCheck:
    requestPath: ${healthCheckPath}
    port: ${healthCheckPort}
    type: ${healthCheckType}
"""
        }

        def manifest = """
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
          ports:
            - containerPort: ${containerPort}
${envBlock}
---
apiVersion: v1
kind: Service
metadata:
  name: ${serviceName}
  namespace: ${namespace}
  labels:
    app: ${deploymentName}
${serviceAnnotationsBlock}
spec:
  type: ${serviceType}
  selector:
    app: ${deploymentName}
  ports:
    - port: ${servicePort}
      targetPort: ${serviceTargetPort}
${backendConfigBlock}
""".stripIndent()

        steps.echo("manifest:")
        steps.echo(manifest)

        def manifestFile = "${steps.env.WORKSPACE}/${deploymentName}-gke-manifest.yaml"

        steps.writeFile(
                file: manifestFile,
                text: manifest
        )

        def applyResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} apply -f ${manifestFile}",
                returnStdout: true
        ).trim()

        steps.echo("applyResult:")
        steps.echo(applyResult)

        def deploymentResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get deployment ${deploymentName} -n ${namespace}",
                returnStdout: true
        ).trim()

        steps.echo("deploymentResult:")
        steps.echo(deploymentResult)

        def serviceResult = steps.sh(
                script: "kubectl --kubeconfig=${kubeConfig} get service ${serviceName} -n ${namespace}",
                returnStdout: true
        ).trim()

        steps.echo("serviceResult:")
        steps.echo(serviceResult)
    }
}