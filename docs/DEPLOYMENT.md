# Optional deployment

## Google sign-in and SMTP

Set Spring's client registration environment variables on the API:

- SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID
- SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET
- SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_SCOPE=openid,profile,email (when overriding defaults)

Register the exact public callback ending in /login/oauth2/code/google with Google. The API requires a verified Google email and tracks the provider subject. An existing password account with the same email stays separate and prevents automatic linking.

For SMTP set SPRING_MAIL_HOST, SPRING_MAIL_PORT, SMTP_FROM and any Spring mail authentication or TLS settings on the worker. Native and Compose use simulated email unless configured. For Compose, inject optional environment variables with a local override file. Restart affected services after changing configuration.

## Kubernetes

The manifests provision a local demonstration namespace via kind. You need Docker, kind, kubectl, and a default storage class. From the project root run bash ./scripts/deploy-kind.sh. This builds and loads both images, creates matching credentials, deploys PostgreSQL, Redis, RabbitMQ, and app services, and port-forwards the frontend to localhost:8080. This is a demo deployment with single-replica stateful services and two API replicas.

## AWS

infra/aws provisions a VPC, subnets, EC2 with SSM access, private RDS PostgreSQL, an encrypted SQS queue and DLQ, a CloudWatch alarm, a Lambda consumer, and a versioned private S3 bucket for release artifacts. Run terraform init and terraform validate in that directory. Terraform plan and apply require AWS credentials and may incur costs. Review the plan, including charges for RDS, EC2, storage, and networking.

The Terraform module provisions infrastructure. Application release installation on EC2 is a separate operator step. Build the JAR, publish it to the S3 artifact bucket, and fetch it on the instance using the EC2 role. Connect through SSM. Configure DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET, ADMIN_PASSWORD and DEMO_MODE=false from your secret manager. Use the RDS endpoint with verified TLS and retrieve the managed RDS secret using the scoped role. Put the API behind your TLS-terminating proxy.

Run a second JAR process with WORKER_MODE=true, SPRING_MAIN_WEB_APPLICATION_TYPE=none, NOTIFICATION_TRANSPORT=sqs, and SQS_QUEUE_URL from Terraform output. That path uses AWS CLI v2 on Linux and the EC2 role. It publishes notification JSON via stdin. Lambda records simulated delivery in CloudWatch. For the AWS demo, that delivery does not populate the local notifications table. If deleting AWS demo infrastructure, handle nonempty versioned S3 buckets and RDS final snapshots as separate cleanup actions.
