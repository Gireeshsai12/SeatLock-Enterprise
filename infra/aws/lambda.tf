data "archive_file" "notification" {
  type        = "zip"
  source_file = "${path.module}/notification.py"
  output_path = "${path.module}/notification.zip"
}
resource "aws_iam_role" "lambda" {
  name = "${var.name}-lambda"
  assume_role_policy = jsonencode({
    Version   = "2012-10-17"
    Statement = [{ Effect = "Allow", Action = "sts:AssumeRole", Principal = { Service = "lambda.amazonaws.com" } }]
  })
}
resource "aws_cloudwatch_log_group" "lambda" {
  name              = "/aws/lambda/${var.name}-notification"
  retention_in_days = 14
}
resource "aws_iam_role_policy" "lambda" {
  role = aws_iam_role.lambda.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      { Effect = "Allow", Action = ["sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"], Resource = aws_sqs_queue.notifications.arn },
      { Effect = "Allow", Action = ["logs:CreateLogStream", "logs:PutLogEvents"], Resource = "${aws_cloudwatch_log_group.lambda.arn}:*" }
    ]
  })
}
resource "aws_lambda_function" "notification" {
  function_name    = "${var.name}-notification"
  filename         = data.archive_file.notification.output_path
  source_code_hash = data.archive_file.notification.output_base64sha256
  role             = aws_iam_role.lambda.arn
  handler          = "notification.handle"
  runtime          = "python3.12"
  timeout          = 10
  depends_on       = [aws_iam_role_policy.lambda]
}
resource "aws_lambda_event_source_mapping" "notification" {
  event_source_arn        = aws_sqs_queue.notifications.arn
  function_name           = aws_lambda_function.notification.arn
  batch_size              = 5
  function_response_types = ["ReportBatchItemFailures"]
}
