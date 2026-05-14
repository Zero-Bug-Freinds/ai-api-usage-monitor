data "aws_availability_zones" "available" {
  state = "available"
}

data "aws_ssm_parameter" "al2023_ami" {
  name = "/aws/service/ami-amazon-linux-latest/al2023-ami-kernel-default-x86_64"
}

locals {
  azs = slice(sort(data.aws_availability_zones.available.names), 0, min(2, length(data.aws_availability_zones.available.names)))
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_support   = true
  enable_dns_hostnames = true

  tags = {
    Name = "${var.project_name}-vpc"
  }
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.project_name}-igw"
  }
}

resource "aws_subnet" "public" {
  count = length(local.azs)

  vpc_id                  = aws_vpc.this.id
  cidr_block              = var.public_subnet_cidrs[count.index]
  availability_zone       = local.azs[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "${var.project_name}-public-${count.index + 1}"
    Tier = "public"
  }
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  tags = {
    Name = "${var.project_name}-public-rt"
  }
}

resource "aws_route" "public_internet" {
  route_table_id         = aws_route_table.public.id
  destination_cidr_block = "0.0.0.0/0"
  gateway_id             = aws_internet_gateway.this.id
}

resource "aws_route_table_association" "public" {
  count = length(aws_subnet.public)

  subnet_id      = aws_subnet.public[count.index].id
  route_table_id = aws_route_table.public.id
}

resource "aws_security_group" "alb" {
  name        = "${var.project_name}-alb-sg"
  description = "ALB ingress"
  vpc_id      = aws_vpc.this.id

  ingress {
    description = "HTTP"
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-alb-sg"
  }
}

resource "aws_security_group" "instance" {
  name        = "${var.project_name}-instance-sg"
  description = "App instances"
  vpc_id      = aws_vpc.this.id

  ingress {
    description     = "App from ALB"
    from_port       = var.target_port
    to_port         = var.target_port
    protocol        = "tcp"
    security_groups = [aws_security_group.alb.id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  tags = {
    Name = "${var.project_name}-instance-sg"
  }
}

resource "aws_security_group_rule" "instance_from_alb_8080" {
  count = var.target_port == 8080 ? 0 : 1

  type                     = "ingress"
  description              = "ALB health check and app traffic on 8080"
  from_port                = 8080
  to_port                  = 8080
  protocol                 = "tcp"
  source_security_group_id = aws_security_group.alb.id
  security_group_id        = aws_security_group.instance.id
}

data "aws_caller_identity" "current" {}
data "aws_region" "current" {}

resource "aws_iam_role" "ec2_instance" {
  name = "${var.project_name}-ec2-instance-${var.environment_label}"

  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect = "Allow"
        Principal = {
          Service = "ec2.amazonaws.com"
        }
        Action = "sts:AssumeRole"
      },
    ]
  })

  tags = {
    Name = "${var.project_name}-ec2-instance-role"
  }
}

resource "aws_iam_role_policy_attachment" "ssm_core" {
  role       = aws_iam_role.ec2_instance.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}

resource "aws_iam_role_policy" "ecr_pull" {
  name = "${var.project_name}-ecr-pull-${var.environment_label}"
  role = aws_iam_role.ec2_instance.id

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
        Sid    = "EcrPullScoped"
        Effect = "Allow"
        Action = [
          "ecr:BatchGetImage",
          "ecr:GetDownloadUrlForLayer",
        ]
        Resource = "arn:aws:ecr:${data.aws_region.current.name}:${data.aws_caller_identity.current.account_id}:repository/${var.ecr_repository_prefix}/*"
      },
    ]
  })
}

resource "aws_iam_instance_profile" "ec2_instance" {
  name = "${var.project_name}-ec2-instance-${var.environment_label}"
  role = aws_iam_role.ec2_instance.name

  tags = {
    Name = "${var.project_name}-ec2-instance-profile"
  }
}

locals {
  user_data = <<-EOT
    #!/bin/bash
    set -euo pipefail
    dnf update -y
    # Required for SSM Run Command (GitHub Actions rolling deploy). AL2023 may omit or disable the agent on some images.
    dnf install -y amazon-ssm-agent
    systemctl enable --now amazon-ssm-agent
    dnf install -y docker
    systemctl enable --now docker
    usermod -aG docker ec2-user || true
    if ! dnf install -y docker-compose-plugin; then
      mkdir -p /usr/local/lib/docker/cli-plugins
      curl -fsSL "https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-$(uname -m)" \
        -o /usr/local/lib/docker/cli-plugins/docker-compose
      chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
    fi
    mkdir -p /opt/${var.project_name}
    chown root:root /opt/${var.project_name}
    chmod 755 /opt/${var.project_name}
  EOT
}

resource "aws_lb_target_group" "app" {
  name_prefix = substr(replace("${var.project_name}-tg", "_", "-"), 0, 6)
  port        = var.target_port
  protocol    = "HTTP"
  vpc_id      = aws_vpc.this.id

  health_check {
    enabled             = true
    path                = var.health_check_path
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 2
    interval            = 15
    timeout             = 5
    protocol            = "HTTP"
    port                = "8080"
  }

  tags = {
    Name = "${var.project_name}-tg"
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_lb" "this" {
  name               = substr(replace("${var.project_name}-alb", "_", "-"), 0, 32)
  load_balancer_type = "application"
  security_groups    = [aws_security_group.alb.id]
  subnets            = aws_subnet.public[*].id

  tags = {
    Name = "${var.project_name}-alb"
  }
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.this.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type             = "forward"
    target_group_arn = aws_lb_target_group.app.arn
  }
}

resource "aws_launch_template" "app" {
  name_prefix = "${var.project_name}-lt-"

  image_id      = data.aws_ssm_parameter.al2023_ami.value
  instance_type = var.instance_type

  iam_instance_profile {
    name = aws_iam_instance_profile.ec2_instance.name
  }

  vpc_security_group_ids = [aws_security_group.instance.id]

  user_data = base64encode(local.user_data)

  tag_specifications {
    resource_type = "instance"
    tags = {
      Name        = "${var.project_name}-app"
      Environment = var.environment_label
    }
  }

  tag_specifications {
    resource_type = "volume"
    tags = {
      Name = "${var.project_name}-app-root"
    }
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_group" "app" {
  name                      = "${var.project_name}-asg-${var.environment_label}"
  vpc_zone_identifier       = aws_subnet.public[*].id
  health_check_type         = "ELB"
  health_check_grace_period = 300
  min_size                  = var.asg_min_size
  max_size                  = var.asg_max_size
  desired_capacity          = var.asg_desired_capacity

  launch_template {
    id      = aws_launch_template.app.id
    version = "$Latest"
  }

  tag {
    key                 = "Name"
    value               = "${var.project_name}-asg-instance"
    propagate_at_launch = true
  }

  tag {
    key                 = "Environment"
    value               = var.environment_label
    propagate_at_launch = true
  }

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_autoscaling_attachment" "app_to_target_group" {
  autoscaling_group_name = aws_autoscaling_group.app.name
  lb_target_group_arn    = aws_lb_target_group.app.arn
}
