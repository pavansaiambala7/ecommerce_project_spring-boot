// CI/CD pipeline: build & test the Spring Boot app, package it as a Docker
// image, push that image to Amazon ECR, then deploy it to the production EC2
// host by having that host pull the new image and restart.
//
// Design choices, and why:
//   - The Jenkins EC2 instance authenticates to ECR via its own IAM instance
//     role (see deploy/iam/jenkins-instance-policy.json). No AWS access keys
//     are stored in Jenkins at all.
//   - The production EC2 host never sees Jenkins credentials or AWS keys
//     either: it has its own narrower IAM role (deploy/iam/prod-instance-
//     policy.json) that only lets it pull this one ECR repo and read this
//     app's own SSM parameters. The only Jenkins credential this pipeline
//     needs is the SSH key used to reach that host.
//   - Runtime secrets (JWT_SECRET, GEMINI_API_KEY, the DB password) live in
//     SSM Parameter Store and are fetched by the production host itself at
//     deploy time (deploy/fetch-secrets.sh) - they pass through Jenkins only
//     as opaque bytes on someone else's disk, never as pipeline variables.
//   - deploy/deploy.sh smoke-tests the new container and automatically rolls
//     back to the last known-good image if it fails, so a bad deploy doesn't
//     leave the site down.
pipeline {
    agent any

    parameters {
        string(name: 'AWS_REGION', defaultValue: 'eu-north-1', description: 'AWS region for ECR and SSM')
        string(name: 'ECR_REPOSITORY', defaultValue: 'ecommerce-app', description: 'ECR repository name')
        // Private IP, not the Elastic IP, on purpose: app-sg allows SSH from
        // jenkins-sg as a source security group, and AWS only applies a
        // source-group reference to traffic arriving on the private address.
        // Dialing the public IP hairpins out through the internet gateway and
        // arrives with Jenkins' public IP as the source, which app-sg does not
        // allow - so it times out. The private IP also survives stop/start.
        string(name: 'PROD_HOST', defaultValue: '172.31.11.210', description: 'Private IP of the app EC2 instance (reachable from Jenkins inside the VPC)')
        string(name: 'PROD_SSH_USER', defaultValue: 'ec2-user', description: 'SSH user on the app EC2 instance')
    }

    environment {
        // No SPRING_DATASOURCE_* here on purpose: PostgresTestBase starts its own
        // Testcontainers PostgreSQL+pgvector container when no external database
        // URL is set, which is what the integration tests actually need on a
        // bare Jenkins host that has no Postgres of its own. Setting a hardcoded
        // localhost URL here (as an earlier version of this file did) makes
        // PostgresTestBase try to connect to a database that doesn't exist,
        // failing the Spring context for every test in the affected classes.
        GEMINI_API_KEY = credentials('gemini-api-key')
        JWT_SECRET = credentials('jwt-secret')
        IMAGE_TAG = "${env.GIT_COMMIT ? env.GIT_COMMIT.take(7) : env.BUILD_NUMBER}"
    }

    options {
        timeout(time: 30, unit: 'MINUTES')
        disableConcurrentBuilds()
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('Build') {
            steps {
                sh './mvnw -B clean compile'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw -B verify'
            }
            post {
                always {
                    junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
                    archiveArtifacts artifacts: 'target/site/jacoco/**', allowEmptyArchive: true
                }
            }
        }

        stage('Package') {
            steps {
                sh './mvnw -B package -DskipTests'
                archiveArtifacts artifacts: 'target/*.jar', fingerprint: true
            }
        }

        stage('Build Docker Image') {
            steps {
                script {
                    env.ECR_REGISTRY = sh(
                        script: "aws sts get-caller-identity --query Account --output text",
                        returnStdout: true
                    ).trim() + ".dkr.ecr.${params.AWS_REGION}.amazonaws.com"
                    env.ECR_IMAGE = "${env.ECR_REGISTRY}/${params.ECR_REPOSITORY}:${env.IMAGE_TAG}"
                }
                sh "docker build -t ${env.ECR_IMAGE} -t ${env.ECR_REGISTRY}/${params.ECR_REPOSITORY}:latest ."
            }
        }

        stage('Push to ECR') {
            steps {
                sh """
                    aws ecr get-login-password --region ${params.AWS_REGION} \
                        | docker login --username AWS --password-stdin ${env.ECR_REGISTRY}
                    docker push ${env.ECR_IMAGE}
                    docker push ${env.ECR_REGISTRY}/${params.ECR_REPOSITORY}:latest
                """
            }
        }

        stage('Deploy to Production') {
            when {
                expression { params.PROD_HOST?.trim() }
            }
            steps {
                sshagent(credentials: ['prod-ec2-ssh-key']) {
                    sh """
                        ssh -o StrictHostKeyChecking=accept-new ${params.PROD_SSH_USER}@${params.PROD_HOST} 'sudo mkdir -p /opt/ecommerce/deploy && sudo chown -R ${params.PROD_SSH_USER}: /opt/ecommerce'
                        scp docker-compose.prod.yml deploy/deploy.sh deploy/fetch-secrets.sh ${params.PROD_SSH_USER}@${params.PROD_HOST}:/opt/ecommerce/deploy/
                        ssh ${params.PROD_SSH_USER}@${params.PROD_HOST} 'chmod +x /opt/ecommerce/deploy/*.sh && /opt/ecommerce/deploy/deploy.sh ${env.ECR_IMAGE} ${params.AWS_REGION}'
                    """
                }
            }
        }
    }

    post {
        failure {
            echo "Pipeline failed. If this happened after 'Deploy to Production' started, deploy.sh should already have rolled the app host back to its last known-good image - check the console log for the rollback outcome."
        }
        cleanup {
            cleanWs()
        }
    }
}
