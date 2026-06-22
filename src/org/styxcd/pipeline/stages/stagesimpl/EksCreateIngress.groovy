package org.styxcd.pipeline.stages.stagesimpl

class EksCreateIngress implements Serializable {

    def steps

    public EksCreateIngress(steps, featureFlags) {
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

                params['INGRESS_ENABLED'] = target?.platform?.ingress?.enabled
                params['INGRESS_NAME'] = target?.platform?.ingress?.name
                params['INGRESS_CLASS_NAME'] = target?.platform?.ingress?.class_name

                def hostsList = []

                target?.platform?.ingress?.hosts?.each { hostRule ->
                    def routesList = []

                    hostRule?.routes?.each { route ->
                        routesList << [
                                path    : route?.path,
                                pathType: route?.path_type,
                                service : route?.service,
                                port    : route?.port
                        ]
                    }

                    hostsList << [
                            host  : hostRule?.host,
                            routes: routesList
                    ]
                }

                params['INGRESS_HOSTS'] = hostsList
            }
        }

        def awsRegion = params['AWS_REGION']
        def clusterName = params['CLUSTER_NAME']
        def namespace = params['NAMESPACE'] ?: 'default'
        def awsAccessKeyCredential = params['AWS_ACCESS_KEY_ID_CREDENTIAL'] ?: 'aws-access-key-id'
        def awsSecretKeyCredential = params['AWS_SECRET_ACCESS_KEY_CREDENTIAL'] ?: 'aws-secret-access-key'

        def ingressEnabled = params['INGRESS_ENABLED']
        def ingressName = params['INGRESS_NAME']
        def ingressClassName = params['INGRESS_CLASS_NAME'] ?: 'alb'
        def ingressHosts = params['INGRESS_HOSTS']

        if (ingressEnabled == false) {
            steps.echo "Ingress is disabled for EKS target ${params['TARGET_NAME']}. Skipping."
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

        if (!ingressClassName?.trim()) {
            steps.error "Missing INGRESS_CLASS_NAME for EKS target ${params['TARGET_NAME']}"
        }

        if (!ingressHosts || ingressHosts.isEmpty()) {
            steps.error "Missing INGRESS_HOSTS for EKS target ${params['TARGET_NAME']}"
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

            def ingressClassResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get ingressclass ${ingressClassName}",
                    returnStdout: true
            ).trim()

            steps.echo "ingressClassResult:"
            steps.echo ingressClassResult

            def rulesBlock = ingressHosts.collect { hostRule ->

                def pathsBlock = hostRule['routes'].collect { route -> """
        - path: ${route['path']}
          pathType: ${route['pathType']}
          backend:
            service:
              name: ${route['service']}
              port:
                number: ${route['port']}
""" }.join('')

                return """
    - host: ${hostRule['host']}
      http:
        paths:
${pathsBlock}
"""
            }.join('')

            def manifest = """
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: ${ingressName}
  namespace: ${namespace}
  annotations:
    kubernetes.io/ingress.class: "${ingressClassName}"
    alb.ingress.kubernetes.io/scheme: internet-facing
    alb.ingress.kubernetes.io/target-type: ip
spec:
  rules:
${rulesBlock}
""".stripIndent()

            steps.echo "manifest:"
            steps.echo manifest

            def manifestFile = "${steps.env.WORKSPACE}/${ingressName}-eks-ingress.yaml"

            steps.writeFile(
                    file: manifestFile,
                    text: manifest
            )

            def applyResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} apply -f ${manifestFile}",
                    returnStdout: true
            ).trim()

            steps.echo "applyResult:"
            steps.echo applyResult

            def ingressResult = steps.sh(
                    script: "kubectl --kubeconfig=${kubeConfig} get ingress ${ingressName} -n ${namespace} -o wide",
                    returnStdout: true
            ).trim()

            steps.echo "ingressResult:"
            steps.echo ingressResult
        }
    }
}