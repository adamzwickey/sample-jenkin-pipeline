import javaposse.jobdsl.dsl.DslFactory

DslFactory dsl = this

// we're parsing the REPOS parameter to retrieve list of repos to build
String repos = binding.variables['REPOS']
List<String> parsedRepos = repos.split(',')
String folderDir = "my-org/pipelines"
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

  //Pipeline Name
	dsl.buildPipelineView("${folderDir}/pipeline") {
    filterBuildQueue()
    filterExecutors()
    title('My Pipeline')
    displayedBuilds(5)
    selectedJob("${my-org}/pipeline")
    alwaysAllowManualTrigger()
    showPipelineParameters()
    refreshFrequency(60)
  }
}
