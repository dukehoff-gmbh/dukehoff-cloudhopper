# 🛠 AWS Setup Guide

This guide helps you prepare your environment for deploying Cloudhopper functions to **AWS Lambda** using the `aws-terraform` generator.

---

## Sign Up for AWS

If you don’t have an AWS account yet:
- Go to [https://aws.amazon.com/](https://aws.amazon.com/) and create an account.
- Set up billing and payment info.

---

## Install Required Tools

### ✅ AWS CLI

Install the AWS CLI:
```bash
# macOS
brew install awscli

# Ubuntu
sudo apt install awscli

# Windows (via MSI installer)
https://docs.aws.amazon.com/cli/latest/userguide/install-cliv2-windows.html
```


## Configure AWS Credentials

Run:
```bash
aws configure
```

Provide:
- Access Key ID
- Secret Access Key
- Default region (e.g. `us-east-1`)

These credentials will be used by Terraform.

To verify:
```bash
aws sts get-caller-identity
```

