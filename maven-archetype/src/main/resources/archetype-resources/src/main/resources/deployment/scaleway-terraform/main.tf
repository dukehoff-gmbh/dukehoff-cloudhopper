# Provider configuration for the generated Terraform in target/deployment/scaleway-terraform.
# The generated files declare the scaleway provider requirement and the variables;
# do not repeat them here.

# Keep the state in an Object Storage bucket instead of the local directory (optional):
# terraform {
#   backend "s3" {
#     bucket                      = "my-terraform-state"
#     key                         = "cloudhopper/terraform.tfstate"
#     region                      = "fr-par"
#     endpoints                   = { s3 = "https://s3.fr-par.scw.cloud" }
#     skip_credentials_validation = true
#     skip_region_validation      = true
#     skip_requesting_account_id  = true
#   }
# }

# Credentials and the project come from ~/.config/scw/config.yaml (scw init) or from
# SCW_ACCESS_KEY, SCW_SECRET_KEY and SCW_DEFAULT_PROJECT_ID.
provider "scaleway" {
  region = "fr-par"
  zone   = "fr-par-1"
}
