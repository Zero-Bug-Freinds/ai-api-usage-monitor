data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

locals {
  github_environment_sub = "repo:${var.github_org}/${var.github_repo}:environment:${var.environment_name}"

  ecr_repository_arn_pattern = "arn:aws:ecr:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository_prefix}/*"

  deploy_elb_resources = length(var.deploy_elb_target_group_arns) > 0 ? var.deploy_elb_target_group_arns : ["*"]
}

resource "aws_iam_role" "release" {
  name = "${var.iam_role_name_prefix}-release-${var.environment_name}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "GitHubActionsOidcRelease"
        Effect = "Allow"
        Principal = {
          Federated = var.oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
            "token.actions.githubusercontent.com:sub" = local.github_environment_sub
          }
        }
      },
    ]
  })
}

resource "aws_iam_role_policy" "release_ecr_push" {
  name = "ecr-push-${var.environment_name}"
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
        Resource = local.ecr_repository_arn_pattern
      },
    ]
  })
}

resource "aws_iam_role" "deploy" {
  name = "${var.iam_role_name_prefix}-deploy-${var.environment_name}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid    = "GitHubActionsOidcDeploy"
        Effect = "Allow"
        Principal = {
          Federated = var.oidc_provider_arn
        }
        Action = "sts:AssumeRoleWithWebIdentity"
        Condition = {
          StringEquals = {
            "token.actions.githubusercontent.com:aud" = "sts.amazonaws.com"
            "token.actions.githubusercontent.com:sub" = local.github_environment_sub
          }
        }
      },
    ]
  })
}

resource "aws_iam_role_policy" "deploy_elb_ssm" {
  name = "elb-ssm-${var.environment_name}"
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
        ]
        Resource = local.deploy_elb_resources
      },
      {
        Sid      = "ElbDescribeTargetHealth"
        Effect   = "Allow"
        Action   = ["elasticloadbalancing:DescribeTargetHealth"]
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
