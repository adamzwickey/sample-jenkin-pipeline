import javaposse.jobdsl.dsl.DslScriptLoader
import javaposse.jobdsl.plugin.JenkinsJobManagement
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.impl.*
import hudson.model.*
import jenkins.model.*
import hudson.plugins.groovy.*
import com.cloudbees.jenkins.plugins.sshcredentials.impl.BasicSSHUserPrivateKey

def jobScript = new File('/usr/share/jenkins/jenkins_pipeline.groovy')
def jobManagement = new JenkinsJobManagement(System.out, [:], new File('.'))

println "Creating the seed job"
new DslScriptLoader(jobManagement).with {
	runScript(jobScript.text
			.replace('http://artifactory', "http://${System.getenv('EXTERNAL_IP') ?: "localhost"}"))
}

String gitUser = new File('/usr/share/jenkins/gituser')?.text ?: "changeme"
String gitKey = new File('/usr/share/jenkins/gitkey')?.text ?: "changeme"


['cf-test', 'cf-stage', 'cf-prod'].each { String id ->
	boolean credsMissing = SystemCredentialsProvider.getInstance().getCredentials().findAll {
		it.getDescriptor().getId() == id
	}.empty
	if (credsMissing) {
		println "Credential [${id}] is missing - will create it"
		SystemCredentialsProvider.getInstance().getCredentials().add(
				new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, id,
						"CF credential [$id]", "admin", "admin"))
		SystemCredentialsProvider.getInstance().save()
	}
}


boolean libRepocredsMissing = SystemCredentialsProvider.getInstance().getCredentials().findAll {
	it.getDescriptor().getId() == 'libRepoCred'
}.empty
if (libRepocredsMissing) {
	println "Credential libRepoCred is missing - will create it"
	SystemCredentialsProvider.getInstance().getCredentials().add(
			new UsernamePasswordCredentialsImpl(CredentialsScope.GLOBAL, 'libRepoCred',
					"LibRepoCred", "admin", "password"))
	SystemCredentialsProvider.getInstance().save()
}


boolean gitCredsMissing = SystemCredentialsProvider.getInstance().getCredentials().findAll {
	it.getDescriptor().getId() == 'git'
}.empty

if (gitCredsMissing) {
	println "Credential [git] is missing - will create it"
	BasicSSHUserPrivateKey.DirectEntryPrivateKeySource privateKey = new BasicSSHUserPrivateKey.DirectEntryPrivateKeySource(gitKey)
	SystemCredentialsProvider.getInstance().getCredentials().add(
			new BasicSSHUserPrivateKey(CredentialsScope.GLOBAL, 'git',
					gitUser, privateKey, null, "Git Credentials"))
	SystemCredentialsProvider.getInstance().save()
}

println "Adding jdk"
Jenkins.getInstance().getJDKs().add(new JDK("jdk8", "/usr/lib/jvm/java-8-openjdk-amd64"))

println "Marking allow macro token"
Groovy.DescriptorImpl descriptor =
		(Groovy.DescriptorImpl) Jenkins.getInstance().getDescriptorOrDie(Groovy)
descriptor.configure(null, net.sf.json.JSONObject.fromObject('''{"allowMacro":"true"}'''))
