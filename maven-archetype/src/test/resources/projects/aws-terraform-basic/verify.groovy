// The generated project must be deployable: the artifact the generated Terraform uploads exists, the
// hand-written main.tf sits next to the generated files, and "terraform validate" passes there (skipped
// when terraform is not on the PATH).
def generator = 'aws-terraform'
// basedir is the IT folder; the generated project is project/<artifactId>
def project = new File(basedir, 'project/aws-demo')
def deployment = new File(project, "target/deployment/${generator}")
assert new File(deployment, 'main.tf').isFile() : "main.tf was not copied to ${deployment}"
// instructions and the skill for coding agents
def agents = new File(project, 'AGENTS.md')
assert agents.isFile() : 'AGENTS.md is missing'
assert agents.text.contains('mvn clean package -Paws-terraform') : 'AGENTS.md does not list the profile'
assert agents.text.contains('Zero cloud SDKs') : 'AGENTS.md has no rule against cloud SDKs'
['.agents/skills/cloudhopper/SKILL.md', '.claude/skills/cloudhopper/SKILL.md'].each { path ->
    def skill = new File(project, path)
    assert skill.isFile() : "${path} is missing"
    assert skill.text.startsWith('---\nname: cloudhopper\n') : "${path} has no skill header"
}
def artifact = 'aws-demo-1.0-SNAPSHOT-aws-terraform.jar'
if (artifact) {
    assert new File(project, "target/${artifact}").isFile() : "target/${artifact} is missing"
}

def run(List<String> command, File dir) {
    def process = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
    def output = process.inputStream.text
    assert process.waitFor() == 0 : "${command.join(' ')} failed:\n${output}"
}

def terraform
try {
    terraform = new ProcessBuilder('terraform', 'version').redirectErrorStream(true).start().with { it.inputStream.text; it.waitFor() == 0 }
} catch (IOException e) {
    terraform = false
}
if (terraform) {
    run(['terraform', 'init', '-backend=false', '-input=false', '-no-color'], deployment)
    run(['terraform', 'validate', '-no-color'], deployment)
    println "terraform validate passed in ${deployment}"
} else {
    // CI sets CLOUDHOPPER_REQUIRE_TERRAFORM, so the check cannot be skipped there unnoticed
    assert System.getenv('CLOUDHOPPER_REQUIRE_TERRAFORM') != 'true' : "terraform not found, PATH=${System.getenv('PATH')}"
    println 'terraform is not on the PATH, skipping terraform validate'
}
return true
