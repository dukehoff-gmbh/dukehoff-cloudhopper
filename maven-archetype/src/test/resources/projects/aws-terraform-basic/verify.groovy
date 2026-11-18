// The generated project must be deployable: the artifact the generated Terraform uploads exists, the
// hand-written main.tf sits next to the generated files, and "terraform validate" passes there (skipped
// when terraform is not on the PATH).
def generator = 'aws-terraform'
// basedir is the IT folder; the generated project is project/<artifactId>
def project = new File(basedir, 'project/aws-demo')
def deployment = new File(project, "target/deployment/${generator}")
assert new File(deployment, 'main.tf').isFile() : "main.tf was not copied to ${deployment}"
def artifact = 'aws-demo-1.0-SNAPSHOT-aws-terraform.jar'
if (artifact) {
    assert new File(project, "target/${artifact}").isFile() : "target/${artifact} is missing"
}

def run(List<String> command, File dir) {
    def process = new ProcessBuilder(command).directory(dir).redirectErrorStream(true).start()
    def output = process.inputStream.text
    assert process.waitFor() == 0 : "${command.join(' ')} failed:\n${output}"
}

def terraform = System.getenv('PATH').split(File.pathSeparator).any { new File(it, 'terraform').canExecute() }
if (terraform) {
    run(['terraform', 'init', '-backend=false', '-input=false', '-no-color'], deployment)
    run(['terraform', 'validate', '-no-color'], deployment)
} else {
    println 'terraform is not on the PATH, skipping terraform validate'
}
return true
