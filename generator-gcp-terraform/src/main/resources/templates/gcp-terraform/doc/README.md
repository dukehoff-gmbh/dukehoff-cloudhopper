 -----------------------------
 Usage Notes
 -----------------------------

 ## Using Cloudhopper-Generated Terraform

 This folder contains auto-generated Terraform resources for GCP.

 ### To test/apply in place:

 1. Ensure required variables are defined (`gcp_project_id`, `gcp_region`, etc.)
 2. (Optional) Edit or rename `main.tf.disabled` to `main.tf`
 3. Initialize Terraform:

     terraform init

 4. Review the plan:

     terraform plan

 5. Apply the changes:

     terraform apply

 ### To integrate into your own Terraform setup:

 - Copy selected `.tf` files from this directory into your existing infrastructure repo.
 - Alternatively, use as a module:

 ```hcl
 module "cloudhopper_gcp" {
   source = "./path/to/generated"
 }
 ```

 For support or updates, see the Cloudhopper documentation.

