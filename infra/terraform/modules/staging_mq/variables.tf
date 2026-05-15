variable "project_name" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "subnet_ids" {
  type        = list(string)
  description = "Subnets for the broker (SINGLE_INSTANCE uses the first subnet)."
}

variable "ec2_security_group_id" {
  type        = string
  description = "Compute instance SG; allowed to reach AMQP(S) on the broker."
}

variable "host_instance_type" {
  type        = string
  description = "Amazon MQ host size for RabbitMQ (e.g. mq.t3.micro — not EC2 t3.micro)."
}

variable "engine_version" {
  type        = string
  description = "RabbitMQ engine version supported by Amazon MQ in this region."
}

variable "username" {
  type        = string
  description = "Broker application user for apps (.env.deploy RABBITMQ_USER)."
}
