package org.styxcd.pipeline.stages.stagesimpl

import org.styxcd.pipeline.utility.MetricsUtil


class EKSWorkflowCleanup implements Serializable {
    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps
    MetricsUtil metricsUtil

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    public EKSWorkflowCleanup(steps, featureFlags) {
        this.steps = steps
        metricsUtil = new MetricsUtil(steps)
    }

    public Map getParams(yml, paramMap) {
        def params = [:]
        params['stagename'] = 'cleanup'
        params['label'] = ''
        params['VALIDATE_MAP'] = paramMap['VALIDATE_MAP']
        params['YML'] = yml
        return params
    }

    public void runStage(script, params, keyMaps) {

      def startTime = System.currentTimeMillis()
      metricsUtil.addStageToSplunkMap(script, "EKSWorkflowCleanup*", startTime, null, keyMaps)

      params.each { entry ->
          steps.echo "Key: ${entry.key} Value: ${entry.value}"
      }

      def yml = params['YML']

      //your cleanup code goes here
      steps.echo "in eks cleanup stage"

      def endTime = System.currentTimeMillis()
      metricsUtil.addStageToSplunkMap(script, "EKSWorkflowCleanup*", startTime, endTime, keyMaps)


      def splunkMap = keyMaps["SPLUNK_MAP"]
      splunkMap["END_TIME"] = endTime
      splunkMap["BUILD_END_TIME"] = endTime - (splunkMap['BUILD_START_TIME'] as Long)
      splunkMap["BUILD_STATUS"] = keyMaps['BUILD_STATUS']
      splunkMap["BUILD_FAILURE_MESSAGE"] = keyMaps['BUILD_FAILURE_MESSAGE']

      def alertMaps = metricsUtil.sendJSONToSplunk(script, splunkMap)
      steps.echo "in method here is splunk response: ${alertMaps['SPLUNK_RESPONSE']}"

      keyMaps.findAll { it.key.startsWith("SPLUNK_STAGE_") }.each { _, value ->
          alertMaps = metricsUtil.sendJSONToSplunk(script, value)
          steps.echo "in stage only part here is splunk response: ${alertMaps['SPLUNK_RESPONSE']}"
      }
    }
}
