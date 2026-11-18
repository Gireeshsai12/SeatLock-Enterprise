terraform {
  required_version = ">= 1.8.0"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.100"
    }
    archive = {
      source  = "hashicorp/archive"
      version = "~> 2.7"
    }
  }
}

provider "aws" {
  region = var.region
}

variable "region" {
  type    = string
  default = "us-east-1"
}
variable "name" {
  type    = string
  default = "seatlock-enterprise"
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{3,29}$", var.name))
    error_message = "Use 4–30 lowercase letters, digits, and hyphens."
  }
}
