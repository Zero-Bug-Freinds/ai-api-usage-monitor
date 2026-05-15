data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  github_oidc_sub_pattern = "repo:${var.github_org}/${var.github_repo}:*"
}

resource "aws_iam_openid_connect_provider" "github" {
  url = "https://token.actions.githubusercontent.com"

  client_id_list = ["sts.amazonaws.com"]

  thumbprint_list = [
    "6938fd4d98bab03faadb97b34396831e3780aea1",
    "1c58a3a8518e8759bf075b76b750d4f2df264fcd",
  ]
}

module "ecr" {
  source = "./modules/ecr"

  repository_prefix                 = var.ecr_repository_prefix
  repository_suffixes               = var.ecr_repository_suffixes
  expire_untagged_images_after_days = var.ecr_untagged_image_expire_days
}

module "compute" {
  count  = var.enable_compute_stack ? 1 : 0
  source = "./modules/compute_stack"

  project_name          = var.project_name
  environment_label     = var.compute_environment_label
  ecr_repository_prefix = var.ecr_repository_prefix
  instance_type         = var.compute_instance_type
  asg_min_size          = var.compute_asg_min_size
  asg_max_size          = var.compute_asg_max_size
  asg_desired_capacity  = var.compute_asg_desired_capacity
  target_port                 = var.alb_target_port
  health_check_path           = var.alb_health_check_path
  health_check_port           = var.alb_health_check_port
  vpc_cidr                    = var.vpc_cidr
  public_subnet_cidrs         = var.public_subnet_cidrs
  bootstrap_git_clone_enabled = var.ec2_bootstrap_git_clone_enabled
  bootstrap_git_clone_url     = var.ec2_bootstrap_git_clone_url != "" ? var.ec2_bootstrap_git_clone_url : "https://github.com/${var.github_org}/${var.github_repo}.git"
}

check "staging_rds_needs_compute_vpc" {
  assert {
    condition     = !var.enable_staging_rds || var.enable_compute_stack
    error_message = "enable_staging_rds requires enable_compute_stack = true (RDS is created in the compute VPC)."
  }
}

module "staging_rds" {
  count  = var.enable_compute_stack && var.enable_staging_rds ? 1 : 0
  source = "./modules/staging_rds"

  project_name          = var.project_name
  vpc_id                = module.compute[0].vpc_id
  subnet_ids            = module.compute[0].public_subnet_ids
  ec2_security_group_id = module.compute[0].instance_security_group_id
  instance_class        = var.staging_rds_instance_class
  allocated_storage     = var.staging_rds_allocated_storage
}

check "compute_asg_capacity_ordering" {
  assert {
    condition = (
      var.compute_asg_min_size <= var.compute_asg_desired_capacity
      && var.compute_asg_desired_capacity <= var.compute_asg_max_size
    )
    error_message = "compute_asg_min_size, compute_asg_desired_capacity, and compute_asg_max_size must satisfy min <= desired <= max."
  }
}

# Doc §1.2 — trust GitHub OIDC for this repository only (sub matches environment jobs too).
resource "aws_iam_role" "release" {
  name = var.release_iam_role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "GitHubActionsOidc"
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github.arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = local.github_oidc_sub_pattern
          }
        }
      },
    ]
  })
}

# Doc §2.1 — ECR push (scoped to prefix/*).
resource "aws_iam_role_policy" "release_ecr_push" {
  name = "ecr-push-release"
  role = aws_iam_role.release.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "EcrAuthToken"
        Effect   = "Allow"
        Action   = ["ecr:GetAuthorizationToken"]
        Resource = "*"
      },
      {
        Sid    = "EcrPushScoped"
        Effect = "Allow"
        Action = [
          "ecr:BatchCheckLayerAvailability",
          "ecr:CompleteLayerUpload",
          "ecr:InitiateLayerUpload",
          "ecr:PutImage",
          "ecr:UploadLayerPart",
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = "arn:aws:ecr:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository_prefix}/*"
      },
      {
        Sid    = "EcrEnsureRepository"
        Effect = "Allow"
        Action = [
          "ecr:CreateRepository",
          "ecr:DescribeRepositories",
        ]
        Resource = "*"
      },
    ]
  })
}

resource "aws_iam_role" "deploy" {
  name = var.deploy_iam_role_name

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "GitHubActionsOidc"
        Effect = "Allow"
        Principal = {
          Federated = aws_iam_openid_connect_provider.github.arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
          }
          StringLike = {
            "token.actions.githubusercontent.com:sub" = local.github_oidc_sub_pattern
          }
        }
      },
    ]
  })
}

# Doc §3.1 — ELB target registration + SSM command (minimal shape).
resource "aws_iam_role_policy" "deploy_elb_ssm" {
  name = "elb-ssm-deploy"
  role = aws_iam_role.deploy.id

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "ElbRegisterDeregister"
        Effect = "Allow"
        Action = [
          "elasticloadbalancing:RegisterTargets",
          "elasticloadbalancing:DeregisterTargets",
          "elasticloadbalancing:DescribeTargetHealth",
        ]
        Resource = "*"
      },
      {
        Sid    = "SsmSendCommand"
        Effect = "Allow"
        Action = [
          "ssm:SendCommand",
          "ssm:GetCommandInvocation",
          "ssm:ListCommandInvocations",
        ]
        Resource = "*"
      },
    ]
  })
}
