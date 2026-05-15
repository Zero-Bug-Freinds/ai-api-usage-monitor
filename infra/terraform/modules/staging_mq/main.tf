# Single-instance Amazon MQ (RabbitMQ) for alpha/staging. Apps on the compute ASG connect via AMQPS.

resource "random_password" "broker" {
  length  = 24
  special = false
}

resource "aws_security_group" "mq" {
  name_prefix = "${substr(replace(var.project_name, "_", "-"), 0, 20)}-mq-"
  description = "Amazon MQ RabbitMQ from app instances only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "AMQPS from app instances"
    from_port       = 5671
    to_port         = 5671
    protocol        = "tcp"
    security_groups = [var.ec2_security_group_id]
  }

  ingress {
    description     = "Management console HTTPS from app instances"
    from_port       = 443
    to_port         = 443
    protocol        = "tcp"
    security_groups = [var.ec2_security_group_id]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.project_name}-staging-mq-sg"
  }
}

resource "aws_mq_broker" "this" {
  broker_name = substr(replace("${var.project_name}-alpha-mq", "_", "-"), 0, 50)

  engine_type        = "RabbitMQ"
  engine_version     = var.engine_version
  host_instance_type = var.host_instance_type
  deployment_mode    = "SINGLE_INSTANCE"

  publicly_accessible        = false
  auto_minor_version_upgrade = true

  subnet_ids      = [var.subnet_ids[0]]
  security_groups = [aws_security_group.mq.id]

  user {
    username = var.username
    password = random_password.broker.result
  }

  logs {
    general = false
  }

  tags = {
    Name = "${var.project_name}-staging-rabbitmq"
  }
}

locals {
  # e.g. amqps://b-abc123.mq.ap-northeast-2.amazonaws.com:5671
  primary_amqp_endpoint = try(aws_mq_broker.this.instances[0].endpoints[0], "")
  amqp_hostname         = length(regexall("^amqps?://([^:/]+)", local.primary_amqp_endpoint)) > 0 ? regex("^amqps?://([^:/]+)", local.primary_amqp_endpoint)[0] : try(aws_mq_broker.this.instances[0].ip_address, "")
  amqp_port             = 5671
  amqp_scheme           = "amqps"
}
