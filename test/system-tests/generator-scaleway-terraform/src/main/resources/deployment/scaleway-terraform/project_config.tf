# A resource of the project's own Terraform whose attribute a function reads: the TCK function
# "projectConfig" reads tck.project. Like most resource attributes, the output is known only
# after the apply, so it cannot be passed through the variable cloudhopper_config.
resource "terraform_data" "tck_project" {
  input = "from the project's Terraform"
}
