resource "aws_db_subnet_group" "main" {
  name       = var.name
  subnet_ids = aws_subnet.database[*].id
}
resource "aws_db_instance" "main" {
  identifier                  = var.name
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = "db.t4g.micro"
  allocated_storage           = 20
  storage_encrypted           = true
  backup_retention_period     = 7
  db_name                     = "seatlock"
  username                    = "seatlock"
  manage_master_user_password = true
  publicly_accessible         = false
  db_subnet_group_name        = aws_db_subnet_group.main.name
  vpc_security_group_ids      = [aws_security_group.db.id]
  skip_final_snapshot         = false
  final_snapshot_identifier   = "${var.name}-final"
}
resource "aws_s3_bucket" "artifacts" {
  bucket_prefix = "${var.name}-"
}
resource "aws_s3_bucket_public_access_block" "artifacts" {
  bucket                  = aws_s3_bucket.artifacts.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}
resource "aws_s3_bucket_versioning" "artifacts" {
  bucket = aws_s3_bucket.artifacts.id
  versioning_configuration {
    status = "Enabled"
  }
}
resource "aws_sqs_queue" "dead" {
  name                      = "${var.name}-dead"
  message_retention_seconds = 1209600
  sqs_managed_sse_enabled   = true
}
resource "aws_sqs_queue" "notifications" {
  name                       = "${var.name}-notifications"
  visibility_timeout_seconds = 90
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead.arn
    maxReceiveCount     = 5
  })
}
resource "aws_cloudwatch_metric_alarm" "dead_letters" {
  alarm_name          = "${var.name}-dead-letters"
  comparison_operator = "GreaterThanThreshold"
  evaluation_periods  = 1
  metric_name         = "ApproximateNumberOfMessagesVisible"
  namespace           = "AWS/SQS"
  period              = 60
  statistic           = "Maximum"
  threshold           = 0
  treat_missing_data  = "notBreaching"
  dimensions          = { QueueName = aws_sqs_queue.dead.name }
}
resource "aws_iam_role" "app" {
  name = "${var.name}-ec2"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "ec2.amazonaws.com" } }]
  })
}
resource "aws_iam_role_policy_attachment" "ssm" {
  role       = aws_iam_role.app.name
  policy_arn = "arn:aws:iam::aws:policy/AmazonSSMManagedInstanceCore"
}
resource "aws_iam_role_policy" "app" {
  role = aws_iam_role.app.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = "sqs:SendMessage", Resource = aws_sqs_queue.notifications.arn },
      { Effect = "Allow", Action = "s3:GetObject", Resource = "${aws_s3_bucket.artifacts.arn}/*" },
      { Effect = "Allow", Action = "secretsmanager:GetSecretValue", Resource = aws_db_instance.main.master_user_secret[0].secret_arn }
    ]
  })
}
resource "aws_iam_instance_profile" "app" {
  name = var.name
  role = aws_iam_role.app.name
}
resource "aws_instance" "app" {
  ami                         = data.aws_ssm_parameter.al2023.value
  instance_type               = "t3.micro"
  subnet_id                   = aws_subnet.app.id
  vpc_security_group_ids      = [aws_security_group.app.id]
  iam_instance_profile        = aws_iam_instance_profile.app.name
  user_data_replace_on_change = true
  user_data                   = "#!/bin/bash\nset -euo pipefail\ndnf install -y java-21-amazon-corretto-headless\n"
  metadata_options {
    http_tokens = "required"
  }
  root_block_device {
    encrypted = true
  }
  tags = { Name = var.name }
}
output "deployment" {
  value = {
    instance_id = aws_instance.app.id
    database    = aws_db_instance.main.endpoint
    db_secret   = aws_db_instance.main.master_user_secret[0].secret_arn
    artifacts   = aws_s3_bucket.artifacts.id
    queue_url   = aws_sqs_queue.notifications.url
  }
}
