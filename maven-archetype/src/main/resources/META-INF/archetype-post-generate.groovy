import org.apache.commons.io.FileUtils

def picked = request.properties['generatorIds']?.split(/\s*,\s*/) ?: []
println "Picked providers: $picked"

// 2) Where the archetype generated (the parent of your new project):
def basedir = new File(request.outputDirectory)

// 3) The new project dir is <basedir>/<artifactId>
def artifactId = request.properties['artifactId']
def projectDir = new File(basedir, artifactId)
println "Project directory is: $projectDir"

// 4) Now prune each un-picked provider folder under that projectDir:
['aws-terraform','gcp-terraform','azure-terraform','scaleway-terraform'].each { p ->
  def tfDir = new File(projectDir, "src/main/resources/deployment/${p}")
  println " → Checking ${tfDir}: exists=${tfDir.exists()}"
  if (!picked.contains(p) && tfDir.exists()) {
    println "    • Deleting unused ${p} directory"
    FileUtils.deleteDirectory(tfDir)
  }
}
// The Agent Skill: .agents/skills is read by Codex, Gemini CLI, Cursor and GitHub Copilot,
// .claude/skills by Claude Code (and GitHub Copilot)
def skills = new File(projectDir, '.agents/skills')
if (skills.isDirectory()) {
  FileUtils.copyDirectory(skills, new File(projectDir, '.claude/skills'))
}
