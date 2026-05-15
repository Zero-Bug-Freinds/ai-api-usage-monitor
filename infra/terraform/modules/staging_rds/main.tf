# Single PostgreSQL RDS for staging-style consolidated DBs (logical DBs per service).
# Production MSA physical separation: see docs/msa-database-and-service-integration.md.

resource "random_password" "master" {
  length  = 24
  special = false
}

resource "aws_security_group" "rds" {
  name_prefix = "${substr(replace(var.project_name, "_", "-"), 0, 20)}-rds-"
  description = "Staging Postgres from ASG instances only"
  vpc_id      = var.vpc_id

  ingress {
    description     = "Postgres from app instances"
    from_port       = 5432
    to_port         = 5432
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
}

resource "aws_db_subnet_group" "this" {
  name_prefix = "${substr(replace(var.project_name, "_", "-"), 0, 10)}-dbsub-"
  subnet_ids  = var.subnet_ids

  lifecycle {
    create_before_destroy = true
  }

  tags = {
    Name = "${var.project_name}-staging-rds-subnets"
  }
}

resource "aws_db_instance" "this" {
  identifier     = "${substr(replace(var.project_name, "_", "-"), 0, 40)}-stg-pg"
  engine         = "postgres"
  engine_version = "16"

  instance_class        = var.instance_class
  allocated_storage     = var.allocated_storage
  max_allocated_storage = 50
  storage_type          = "gp3"

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [aws_security_group.rds.id]

  username = "appadmin"
  password = random_password.master.result

  skip_final_snapshot        = true
  publicly_accessible        = false
  backup_retention_period    = 1
  deletion_protection        = false
  auto_minor_version_upgrade = true

  tags = {
    Name = "${var.project_name}-staging-postgres"
  }
}
