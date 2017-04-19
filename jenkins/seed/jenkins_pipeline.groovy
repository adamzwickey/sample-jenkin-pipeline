import javaposse.jobdsl.dsl.DslFactory

DslFactory factory = this

String repos = 'git@github.com:pivotalservices/sample-jenkin-pipeline.git'

factory.job('jenkins-pipeline-seed') {
    scm {
        git {
            remote {
                url('git@github.com:pivotalservices/sample-jenkin-pipeline.git')
                credentials('git')
            }
            branch('master')
        }
    }
    wrappers {
        parameters {
            stringParam('REPOS', repos,
                    "Provide a comma separated list of repos. If you want the project name to be different then repo name, " +
                            "first provide the name and separate the url with \$ sign")
            stringParam('GIT_CREDENTIAL_ID', 'git', 'ID of the credentials used to push tags to git repo')
            stringParam('CF_TEST_CREDENTIAL_ID', 'cf-test', 'ID of the credentials used to push to test env')
            stringParam('CF_STAGE_CREDENTIAL_ID', 'cf-stage', 'ID of the credentials used to push to stage env')
            stringParam('CF_PROD_CREDENTIAL_ID', 'cf-prod', 'ID of the credentials used to push prod env')
            stringParam('LIB_REPO_CRED_ID', 'libRepoCred', 'ID the username and password to access lib repo artifactory/nexus')
            stringParam('JDK_VERSION', 'jdk8', 'ID of Git installation')
            stringParam('CF_TEST_API_URL', 'api.local.pcfdev.io', 'URL to CF Api for test env')
            stringParam('CF_STAGE_API_URL', 'api.local.pcfdev.io', 'URL to CF Api for stage env')
            stringParam('CF_PROD_API_URL', 'api.local.pcfdev.io', 'URL to CF Api for prod env')
            stringParam('CF_TEST_ORG', 'pcfdev-org', 'Name of the CF organization for test env')
            stringParam('CF_TEST_SPACE', 'pcfdev-test', 'Name of the CF space for test env')
            stringParam('CF_STAGE_ORG', 'pcfdev-org', 'Name of the CF organization for stage env')
            stringParam('CF_STAGE_SPACE', 'pcfdev-stage', 'Name of the CF space for stage env')
            stringParam('CF_PROD_ORG', 'pcfdev-org', 'Name of the CF organization for prod env')
            stringParam('CF_PROD_SPACE', 'pcfdev-prod', 'Name of the CF space for prod env')
            stringParam('REPO_WITH_JARS', 'http://artifactory:8081/artifactory/libs-release-local', "Address to hosted JARs")
            stringParam('GIT_EMAIL', 'sding@pivotal.io', "Email used to tag the repo")
            stringParam('GIT_NAME', 'Pivo Tal', "Name used to tag the repo")
            stringParam('PROD_INSTANCES', '2', "Number of instances deploy to prod")
            stringParam('TEST_DOMAIN', '', "TEST DOMAIN")
            stringParam('STAGE_DOMAIN', '', "STAGE DOMAIN")
            stringParam('PROD_DOMAIN', '', "PROD DOMAIN")
            stringParam('GIT_BRANCH', 'master', "branch used to build")
        }
    }
    steps {
        gradle("clean build")
        dsl {
            external('jenkins/jobs/pipeline.groovy','jenkins/jobs/pipeline_view.groovy')
            removeAction('DISABLE')
            removeViewAction('DELETE')
            ignoreExisting(false)
            lookupStrategy('SEED_JOB')
            additionalClasspath([
                'jenkins/src/main/groovy', 'jenkins/src/main/resources'
            ].join("\n"))
        }
    }
}
