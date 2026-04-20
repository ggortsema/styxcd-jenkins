package org.styxcd.pipeline

class FeatureFlags implements Serializable {

    /**
     * test feature flag
     *
     */
    public static final String FFTEST = "fftest"

    private def flagDetails = [
            (FFTEST): [
                    enabled: false
            ]
    ]

    /**
     * A reference to the pipeline that allows you to run pipeline steps in your shared library
     */
    def steps

    /**
     * Constructor
     *
     * @param steps a reference to the pipeline that allows you to run pipeline steps in your shared libary
     * @param flagCsv csv string of flags, probably from an env var
     * @param flags list of flags to enable
     */
    FeatureFlags(steps, String flagCsv, List<String> flags) {
        this.steps = steps
        def finalFlags = []

        if (flagCsv) {
            finalFlags = flagCsv.split(',').toList()
        }

        if (flags) {
            finalFlags += flags
        }

        finalFlags.each {
            def flag = it.trim().toLowerCase()
            if (flagDetails.containsKey(flag)) {
                flagDetails[flag].enabled = true
            }
        }
    }

    /**
     * Returns if a feature flag is enabled
     *
     * @param flag name of the feature being queried
     * @return Boolean indicating if flag is enabled
     */
    public Boolean isEnabled(String flag) {
        if (flagDetails.containsKey(flag)) {
            return flagDetails[flag].enabled
        }

        return false
    }

    /**
     * Echos all enabled feature flags
     */
    public void prettyPrint() {
        def message = 'Enabled Feature Flags:\n'
        def printed = false
        flagDetails.each { k, v ->
            if (v.enabled) {
                message += "${k}\n"
                printed = true
            }
        }

        if (!printed) {
            message += 'NONE'
        }

        steps.echo(message)
    }

    /**
     * Returns a list of enabled flags
     */
    public List<String> getEnabledFlags() {
        def enabledFlags = []

        flagDetails.each { k, v ->
            if (v.enabled) {
                enabledFlags << k
            }
        }

        return enabledFlags
    }
}
