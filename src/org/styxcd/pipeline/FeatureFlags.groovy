package org.styxcd.pipeline

class FeatureFlags implements Serializable {

    public static final String FFTEST = "fftest"

    private def flagDetails = [
            (FFTEST): [
                    enabled: false
            ]
    ]

    def steps

    FeatureFlags(steps, String flagCsv, Object flags) {
        this.steps = steps

        def finalFlags = []

        if (flagCsv) {
            finalFlags += flagCsv.split(',').toList()
        }

        finalFlags += normalizeFlags(flags)

        finalFlags.each {
            def flag = it?.toString()?.trim()?.toLowerCase()

            if (flag && flagDetails.containsKey(flag)) {
                flagDetails[flag].enabled = true
            }
        }
    }

    private List<String> normalizeFlags(Object flags) {
        def normalizedFlags = []

        if (!flags) {
            return normalizedFlags
        }

        if (flags instanceof List) {
            normalizedFlags += flags
        } else if (flags instanceof String) {
            normalizedFlags += flags.split(',').toList()
        } else if (flags instanceof Map) {
            flags.each { key, value ->
                if (value == true) {
                    normalizedFlags << key
                }
            }
        }

        return normalizedFlags
    }

    public Boolean isEnabled(String flag) {
        if (!flag) {
            return false
        }

        def normalizedFlag = flag.trim().toLowerCase()

        if (flagDetails.containsKey(normalizedFlag)) {
            return flagDetails[normalizedFlag].enabled
        }

        return false
    }

    public void prettyPrint() {
        def message = 'Enabled Feature Flags:\n'
        def printed = false

        flagDetails.each { key, value ->
            if (value.enabled) {
                message += "${key}\n"
                printed = true
            }
        }

        if (!printed) {
            message += 'NONE'
        }

        steps.echo(message)
    }

    public List<String> getEnabledFlags() {
        def enabledFlags = []

        flagDetails.each { key, value ->
            if (value.enabled) {
                enabledFlags << key
            }
        }

        return enabledFlags
    }
}