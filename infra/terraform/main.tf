data "aws_caller_identity" "current" {}

locals {
  ecr_repository_suffixes = [
    "api-gateway-service",
    "proxy-service",
    "identity-service",
    "usage-service",
    "billing-service",
    "team-service",
    "notification-service",
    "agent-service",
    "identity-web",
    "usage-web",
    "billing-web",
    "team-web",
    "notification-web",
    "agent-web",
    "web-edge",
  ]

  deploy_elb_target_group_arns = distinct(concat(
    var.enable_compute_stack ? [module.compute[0].alb_target_group_arn] : [],
    var.extra_deploy_elb_target_group_arns,
  ))
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
  repository_suffixes               = local.ecr_repository_suffixes
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
  target_port           = var.alb_target_port
  health_check_path     = var.alb_health_check_path
  vpc_cidr              = var.vpc_cidr
  public_subnet_cidrs   = var.public_subnet_cidrs
}

module "github_env_roles" {
  for_each = toset(["staging", "production"])

  source = "./modules/github_env_roles"

  environment_name             = each.key
  github_org                   = var.github_org
  github_repo                  = var.github_repo
  oidc_provider_arn            = aws_iam_openid_connect_provider.github.arn
  iam_role_name_prefix         = var.iam_role_name_prefix
  ecr_repository_prefix        = var.ecr_repository_prefix
  deploy_elb_target_group_arns = local.deploy_elb_target_group_arns
}
