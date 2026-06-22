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
            }
        }

        def applicationName = params['APPLICATION_NAME']

        def applicationConfig = null
        yml.release?.applications?.each { app ->
            if (app?.name == applicationName) {
                applicationConfig = app
            }
        }

        if (applicationConfig == null) {
            steps.error "Could not find application config for APPLICATION_NAME ${applicationName}"
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def namespace = params['NAMESPACE'] ?: 'default'
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        def deploymentName = applicationConfig?.deployment_name ?: applicationConfig?.name
        def serviceName = applicationConfig?.service?.name ?: deploymentName
        def image = applicationConfig?.image
        def replicas = applicationConfig?.replicas ?: 1
        def containerPort = applicationConfig?.container?.port
        def servicePort = applicationConfig?.service?.port ?: 80
        def serviceTargetPort = applicationConfig?.service?.target_port ?: containerPort

        if (!awsRegion?.trim()) {
            steps.error "Missing AWS_REGION for EKS target ${params['TARGET_NAME']}"
        }

        if (!clusterName?.trim()) {
            steps.error "Missing CLUSTER_NAME for EKS target ${params['TARGET_NAME']}"
        }

        if (!namespace?.trim()) {
            steps.error "Missing NAMESPACE for EKS target ${params['TARGET_NAME']}"
        }

        if (!applicationName?.trim()) {
            steps.error "Missing APPLICATION_NAME"
        }

        if (!deploymentName?.trim()) {
            steps.error "Missing deployment name for application ${applicationName}"
        }

        if (!image?.trim()) {
            steps.error "Missing image for application ${applicationName}"
        }

        if (!containerPort) {
            steps.error "Missing container.port for application ${applicationName}"
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

            def namespaceResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get namespace ${namespace}",
                    returnStdout: true
            ).trim()

            steps.echo "namespaceResult:"
            steps.echo namespaceResult

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
  type: ClusterIP
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