package org.styxcd.pipeline.utility

import org.styxcd.pipeline.Constants
import org.styxcd.pipeline.FeatureFlags

/**
 * Utility that performs various maven tasks as well as other tasks tied to it such as git.
 * peforms tasks such as build, interact with sonar, run integration tests, git pull, etc.
 *
 * @Author grant.gortsema@gmail.com
 */
class MavenUtil implements Serializable {

    /**
     * a reference to the pipeline that allows you to run pipeline steps in your shared libary
     */
    def steps

    /**
     * a reference to current feature flags
     */
    FeatureFlags featureFlags

    /**
     * Constructor
     *
     * @param steps A reference to the steps in a pipeline to give access to them in the shared library.
     */
    public MavenUtil(steps, featureFlags) { 
        this.steps = steps
        this.featureFlags = featureFlags
    }

    /**
     * Pulls code from a git repo
     *
     * @param script reference to pipeline script used to access global variables. Usually passed in as 'this' from the pipeline
     * @param repo is the URL to pull the git code from
     * @param branch the branch for the git repo. if this param is ommited it will use default branch
     *
     */
    public void gitPull(script, String repo, String branch = null) {

        steps.echo "Cloning repo: ${repo} and branch: ${branch}"

        steps.git branch: "${branch}", url: "${repo}"
    }
}
