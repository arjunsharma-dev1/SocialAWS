# Environment Variables

- S3_ARTIFACTS_BUCKET --> S3 bucket name
- REGION --> AWS Region (e.g. ap-south-1)
- AWS_SOCIALAWS_SQS --> SQS name(e.g. PostCreationQueue)
- AWS_ACCOUNT_ID --> User's AWS Account ID
- amazon_aws_accesskey --> AWS Access Key
- amazon_aws_secretkey --> AWS Secret Key


# Architecture
![diagram-export-6-10-2024-11_31_59-PM.png](..%2Fassets%2Fdiagram-export-6-10-2024-11_31_59-PM.png)

- There is one VPC(Virtual Private Cloud),
  where we have one AZ(Availability Zone) which is ap-south-1(i.e. Mumbai).

- set REGION environment variable to set region on your machine.(Important, no default value)

- Inside the VPC we have a single EC2 instance, having Java JAR, which is a RESTful API.

### Fetches Posts Present in the DynamoDB Table
```
curl hostname:8080/posts
```
- Sends a Scan Request to DynamoDB table named `TweetTable` to fetch all the posts present

### Saves Posts to the DynamoDB Table
```
curl -X POST -H 'Content-type: application/json' -d '{"text": "Sample10"}' hostname:8080/posts
```
- Pushes Posts to the SQS named `PostCreationQueue`.
- then a lambda named `LambdaApp` fetches the Event from the queue & then saves it into DyanmoDB table named `TweetTable`

### Security Group
- named `ec2-sg`.
- Allows all outbound requests
- Only allow request for anywhere for port 22, 80, 8080(App).

### NACL
- named `SocialAWS-NACL`
- allows HTTP & SSH inbound/outbound network
- allows outbound traffic from ports in range from 1024-65535

### Startup Script for EC2
- To deploy to infra using CDK, S3 mentioned in environment variable named `S3_ARTIFACTS_BUCKET`(important), must have `jdk-21.0.2_linux-x64_bin.tar.gz` exactly this file & `socialaws.jar` from `assets` after running `mvn clean package`, these two files must be present at root of mentioned S3.
- unzip JDK 21
- sets JAVA_HOME
- sets JAVA Path to PATH variable
- sets REGION, AWS_ACCOUNT_ID & AWS_SOCIALAWS_SQS environment variables in EC2
