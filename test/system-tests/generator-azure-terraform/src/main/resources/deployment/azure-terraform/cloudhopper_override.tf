# Passes values of this project's Terraform to the functions: an override file may redefine
# the locals that the generated files declare empty.
locals {
  cloudhopper_extra_config = {
    "tck.project" = terraform_data.tck_project.output
  }
}
