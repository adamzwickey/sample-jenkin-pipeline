import javaposse.jobdsl.dsl.DslFactory
import javaposse.jobdsl.dsl.helpers.BuildParametersContext

/*
	TODO: TO develop
	- write bash tests
	- perform blue green deployment
	- implement the complete step
*/

DslFactory dsl = this

// These will be taken either from seed or global variables
PipelineDefaults defaults = new PipelineDefaults(binding.variables)

// Example of a version with date and time in the name
String pipelineVersion = binding.variables["PIPELINE_VERSION"] ?: '''1.0.0.M1-${GROOVY,script ="new Date().format('yyMMdd_HHmmss')"}-VERSION'''
String cronValue = '*/5 * * * *'
String testReports = ["**/surefire-reports/*.xml", "**/test-results/**/*.xml"].join(",")
String gitCredentials = binding.variables["GIT_CREDENTIAL_ID"] ?: "git"
String jdkVersion = binding.variables["JDK_VERSION"] ?: "jdk8"
String gitEmail = binding.variables["GIT_EMAIL"] ?: "pivo@tal.com"
String gitName = binding.variables["GIT_NAME"] ?: "Pivo Tal"
String gitBranch = binding.variables["GIT_BRANCH"]?: "master"
String folderDir = binding.variables["JENKIN_FOLDER"]?: "digital-shared-org"
String scriptsDir = binding.variables["SCRIPTS_DIR"] ?: "${WORKSPACE}/jenkins/common/src/main/bash"
String testEnv = binding.variables["TEST_ENV"]

// we're parsing the REPOS parameter to retrieve list of repos to build
String repos = binding.variables["REPOS"]
String testGitRepo = binding.variables["TEST_GIT_REPO"]
String testBranch= binding.variables["TEST_GIT_BRANCH"]

List<String> parsedRepos = repos.split(",")
parsedRepos.each {
	List<String> parsedEntry = it.split('\\$')
	String gitRepoName
	String fullGitRepo
	if (parsedEntry.size() > 1) {
		gitRepoName = parsedEntry[0]
		fullGitRepo = parsedEntry[1]
	} else {
		gitRepoName = parsedEntry[0].split('/').last()
		fullGitRepo = parsedEntry[0]
	}
	String projectName = "${gitRepoName}-pipeline"

	//  ======= JOBS =======
	dsl.job("${folderDir}/${projectName}-build") {
		triggers {
			scm(cronValue)
		}
		wrappers {
		  injectPasswords {
		    injectGlobalPasswords()
			}

			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		jdk(jdkVersion)
		scm {
			git {
				remote {
					name('origin')
					url(fullGitRepo)
					branch('${GIT_BRANCH}')
					credentials(gitCredentials)
				}
				extensions {
					wipeOutWorkspace()
				}
			}
		}
		configure { def project ->
			// Adding user email and name here instead of global settings
			project / 'scm' / 'extensions' << 'hudson.plugins.git.extensions.impl.UserIdentity' {
				'email'(gitEmail)
				'name'(gitName)
			}
		}
		steps {
			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/build_and_upload.sh')}
		""")
		  environmentVariables {
          propertiesFile('my_env.properties')
      }
		}
		publishers {
			archiveJunit(testReports)
			downstreamParameterized {
				trigger("${projectName}-test-env-deploy") {
					triggerWithNoParameters()
					parameters {
						gitRevision()
						currentBuild()
						predefinedProp('PIPELINE_VERSION', '${PIPELINE_VERSION}')
						predefinedProp('PROJECT_ARTIFACT_ID', '${PROJECT_ARTIFACT_ID}')
						predefinedProp('PROJECT_GROUP_ID', '${PROJECT_GROUP_ID}')
					}
				}
			}
			git {
				tag('origin', '${PIPELINE_VERSION}') {
					pushOnlyIfSuccess()
					create()
					update()
				}
			}
		}
	}

	dsl.job("${folderDir}/${projectName}-test-env-deploy") {
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			customTools(['cf_cli_6.22.1']) {
						skipMasterInstallation()
			}
			parameters {
					stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
					stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
					stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		scm {
			git {
				remote {
					name('origin')
					url(fullGitRepo)
					branch('${GIT_BRANCH}')
					credentials(gitCredentials)
				}
				extensions {
					wipeOutWorkspace()
				}
			}
		}
		// TODO param branch
		steps {
			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/test_deploy.sh')}
		""")
		}
		publishers {
			downstreamParameterized {
				trigger("${projectName}-test-env-test") {
					parameters {
						currentBuild()
						gitRevision()
						predefinedProp('PIPELINE_VERSION', '${PIPELINE_VERSION}')
						predefinedProp('PROJECT_ARTIFACT_ID', '${PROJECT_ARTIFACT_ID}')
						predefinedProp('PROJECT_GROUP_ID', '${PROJECT_GROUP_ID}')
					}
					triggerWithNoParameters()
				}
			}
		}
	}

	dsl.job("${folderDir}/${projectName}-test-env-test") {
		//deliveryPipelineConfiguration('Test', 'Tests on test')
		label('zone3')
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			parameters {
					stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
					stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
					stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				env('envParameter',testEnv)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		scm {
			git {
				remote {
					name('origin')
					url(fullGitRepo)
					branch('${GIT_BRANCH}')
					credentials(gitCredentials)
				}
				extensions {
					wipeOutWorkspace()
				}
			}
		}
		steps {

			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/test_smoke.sh')}
		""")
		}
		publishers {
			extendedEmail {
				recipientList('abc@test.com')
				defaultSubject('Test Job Failed')
				defaultContent('Something broken')
				contentType('text/html')
				triggers {
					always()
				}
			}
			downstreamParameterized {
				trigger("${projectName}-test-env-load") {
					parameters {
						currentBuild()
//						gitRevision()
						predefinedProp('PIPELINE_VERSION', '${PIPELINE_VERSION}')
						predefinedProp('PROJECT_ARTIFACT_ID', '${PROJECT_ARTIFACT_ID}')
						predefinedProp('PROJECT_GROUP_ID', '${PROJECT_GROUP_ID}')
					}
					triggerWithNoParameters()
				}
			}
		}
	}

	//Adding Load Tests here
	dsl.job("${folderDir}/${projectName}-test-env-load") {
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			parameters {
				stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
				stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
				stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		scm {
			git {
				remote {
					name('origin')
					url(fullGitRepo)
					branch('${GIT_BRANCH}')
					credentials(gitCredentials)
				}
				extensions {
					wipeOutWorkspace()
				}
			}
		}


		def loadSwitches = '''\
        -PnexusPublicRepoURL=${nexusPublicRepoURL}
        -PnexusReleaseRepoURL=${nexusReleaseRepoURL}
        -PnexusSnapshotRepoURL=${nexusSnapshotRepoURL}
        -PnexusUsername=${nexusUsername}
        -PnexusPassword=${nexusPassword}
        -PcfUsername=${cfUsername}
        -PcfPassword=${cfPassword}
        -Pclassifier=RC${BUILD_NUMBER}
        -PbuildNumber=${BUILD_NUMBER}
        -PARTIFACT_TYPE=${RELEASE_TYPE}
        -Dapp_base_url=https://abc.com
        -Dhttp.proxyHost=170.217.74.84
        -Dhttp.proxyPort=8080
        -Dhttps.proxyHost=170.217.74.84
        -Dhttps.proxyPort=8080
        '''.stripIndent()

		// TODO param branch
		steps {
			gradle {
				switches(loadSwitches)
				tasks("gatlingRun")

			}
			}
		publishers {
			archiveArtifacts {
				pattern('build/reports/gatling/**')
			}
			downstreamParameterized {
				trigger("${projectName}-staging-env-deploy") {
					parameters {
						currentBuild()
						gitRevision()
						predefinedProp('PIPELINE_VERSION', '${PIPELINE_VERSION}')
						predefinedProp('PROJECT_ARTIFACT_ID', '${PROJECT_ARTIFACT_ID}')
						predefinedProp('PROJECT_GROUP_ID', '${PROJECT_GROUP_ID}')
					}
					triggerWithNoParameters()
				}
			}
		}



	}

	dsl.job("${folderDir}/${projectName}-staging-env-deploy") {
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			customTools(['cf_cli_6.22.1']) {
						skipMasterInstallation()
			}
			parameters {
					stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
					stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
					stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		scm {
			git {
				remote {
					name('origin')
					url(fullGitRepo)
					branch('${GIT_BRANCH}')
					credentials(gitCredentials)
				}
				extensions {
					wipeOutWorkspace()
				}
			}
		}
		steps {
			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/stage_deploy.sh')}
		""")
		}

		publishers {
			buildPipelineTrigger("${folderDir}/${projectName}-prod-env-deploy") {
					parameters {
						currentBuild()
						gitRevision()
						predefinedProp('PIPELINE_VERSION', '${PIPELINE_VERSION}')
						predefinedProp('PROJECT_ARTIFACT_ID', '${PROJECT_ARTIFACT_ID}')
						predefinedProp('PROJECT_GROUP_ID', '${PROJECT_GROUP_ID}')
					}
			}
		}
	}

	dsl.job("${folderDir}/${projectName}-prod-env-deploy") {
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			customTools(['cf_cli_6.22.1']) {
						skipMasterInstallation()
			}
			parameters {
					stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
					stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
					stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		scm {
			git {
				remote {
					name('origin')
					url(fullGitRepo)
					branch('${GIT_BRANCH}')
					credentials(gitCredentials)
				}
				extensions {
					wipeOutWorkspace()
				}
			}
		}
		// TODO param branch
		steps {
			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/prod_deploy.sh')}
		""")
		}
		publishers {
			buildPipelineTrigger("${folderDir}/${projectName}-promote-new-release, ${folderDir}/${projectName}-remove-canary") {
					parameters {
						currentBuild()
						gitRevision()
						predefinedProp('PIPELINE_VERSION', '${PIPELINE_VERSION}')
						predefinedProp('PROJECT_ARTIFACT_ID', '${PROJECT_ARTIFACT_ID}')
						predefinedProp('PROJECT_GROUP_ID', '${PROJECT_GROUP_ID}')
					}
			}
		}
	}

	dsl.job("${folderDir}/${projectName}-promote-new-release") {
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			customTools(['cf_cli_6.22.1']) {
            skipMasterInstallation()
      }
			parameters {
					stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
					stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
					stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		steps {
			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/promote_new_release.sh')}
		""")
		}
	}

	dsl.job("${folderDir}/${projectName}-remove-canary") {
		wrappers {
			injectPasswords {
				injectGlobalPasswords()
			}
			customTools(['cf_cli_6.22.1']) {
            skipMasterInstallation()
      }
			parameters {
					stringParam('PIPELINE_VERSION', '', 'PIPE Line version')
					stringParam('PROJECT_GROUP_ID', '', 'Project Group ID')
					stringParam('PROJECT_ARTIFACT_ID', '', 'Project Artifact ID')
			}
			environmentVariables {
				environmentVariables(defaults.defaultEnvVars)
			}
			maskPasswords()
			timeout {
				noActivity(300)
				failBuild()
				writeDescription('Build failed due to timeout after {0} minutes of inactivity')
			}
		}
		steps {
			shell("""#!/bin/bash
		set -e

		${dsl.readFileFromWorkspace(scriptsDir + '/pipeline.sh')}
		${dsl.readFileFromWorkspace(scriptsDir + '/remove_canary.sh')}
		""")
		}
	}

}

//  ======= JOBS =======

/**
 * A helper class to provide delegation for Closures. That way your IDE will help you in defining parameters.
 * Also it contains the default env vars setting
 */
class PipelineDefaults {

	final Map<String, String> defaultEnvVars

	PipelineDefaults(Map<String, String> variables) {
		this.defaultEnvVars = defaultEnvVars(variables)
	}

	private Map<String, String> defaultEnvVars(Map<String, String> variables) {
		Map<String, String> envs = [:]
		envs['CF_TEST_API_URL'] = variables['CF_TEST_API_URL'] ?: 'api.local.pcfdev.io'
		envs['CF_STAGE_API_URL'] = variables['CF_STAGE_API_URL'] ?: 'api.local.pcfdev.io'
		envs['CF_PROD_API_URL'] = variables['CF_PROD_API_URL'] ?: 'api.local.pcfdev.io'
		envs['CF_TEST_ORG'] = variables['CF_TEST_ORG'] ?: 'pcfdev-org'
		envs['CF_TEST_SPACE'] = variables['CF_TEST_SPACE'] ?: 'pfcdev-test'
		envs['CF_STAGE_ORG'] = variables['CF_STAGE_ORG'] ?: 'pcfdev-org'
		envs['CF_STAGE_SPACE'] = variables['CF_STAGE_SPACE'] ?: 'pfcdev-stage'
		envs['CF_PROD_ORG'] = variables['CF_PROD_ORG'] ?: 'pcfdev-org'
		envs['CF_PROD_SPACE'] = variables['CF_PROD_SPACE'] ?: 'pfcdev-prod'
		envs['REPO_WITH_JARS'] = variables['REPO_WITH_JARS'] ?: 'http://artifactory:8081/artifactory/libs-release-local'
		envs['ARTIFACT_TYPE'] = variables['ARTIFACT_TYPE'] ?: 'RELEASE'
		envs['TEST_DOMAIN'] = variables['TEST_DOMAIN'] ?: ''
		envs['STAGE_DOMAIN'] = variables['STAGE_DOMAIN'] ?: ''
		envs['PROD_DOMAIN'] = variables['PROD_DOMAIN'] ?: ''
		envs['GIT_BRANCH'] = variables['GIT_BRANCH'] ?: ''
		envs['CF_HOME'] = '.'
		envs['PROD_INSTANCES'] = variables['PROD_INSTANCES']
		return envs
	}

	public static final String groovyEnvScript = '''
String workspace = binding.variables['WORKSPACE']
String mvn = "${workspace}/mvnw"
String gradle =  "${workspace}/gradlew"

Map envs = [:]
if (new File(mvn).exists()) {
	envs['PROJECT_TYPE'] = "MAVEN"
	envs['OUTPUT_FOLDER'] = "target"
} else if (new File(gradle).exists()) {
	envs['PROJECT_TYPE'] = "GRADLE"
	envs['OUTPUT_FOLDER'] = "build/libs"
}
return envs'''

	protected static Closure context(@DelegatesTo(BuildParametersContext) Closure params) {
		params.resolveStrategy = Closure.DELEGATE_FIRST
		return params
	}

	/**
	 * With the Security constraints in Jenkins in order to pass the parameters between jobs, every job
	 * has to define the parameters on input. In order not to copy paste the params we're doing this
	 * default params method.
	 */
	static Closure defaultParams() {
		return context {
			//booleanParam('REDOWNLOAD_INFRA', false, "If Eureka & StubRunner & CF binaries should be redownloaded if already present")
		}
	}

	/**
	 * With the Security constraints in Jenkins in order to pass the parameters between jobs, every job
	 * has to define the parameters on input. We provide additional smoke tests parameters.
	 */
	static Closure smokeTestParams() {
		return context {
			stringParam('TEST_DOMAIN', '', "URL of test domain")
		}
	}
}
