package com.myorg;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.lambda.Code;
import software.amazon.awscdk.services.lambda.Function;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.lambda.eventsources.SqsEventSource;
import software.amazon.awscdk.services.lightsail.CfnStaticIp;
import software.amazon.awscdk.services.opsworks.CfnStack;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.deployment.BucketDeployment;
import software.amazon.awscdk.services.s3.deployment.BucketDeploymentProps;
import software.amazon.awscdk.services.s3.deployment.Source;
import software.amazon.awscdk.services.sqs.Queue;
import software.constructs.Construct;

import java.util.List;

public class InfraStack extends Stack {

    public InfraStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public InfraStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);

        // The code that defines your stack goes here

        var postCreationQueue = Queue.Builder.create(this, "PostCreationQueue")
                .queueName("PostCreationQueue")
                .retentionPeriod(Duration.hours(1))
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        var postCreationHandlerFunction = Function.Builder.create(this, "LambdaApp")
                .functionName("LambdaApp")
                .runtime(Runtime.JAVA_21)
                .code(Code.fromAsset("../assets/lambdas.jar"))
                .handler("org.encode_decoder.LambdaApp")
                .timeout(Duration.seconds(30))
                .events(
                        List.of(new SqsEventSource(postCreationQueue))
                )
                .build();

        var tweetTable = Table.Builder.create(this, "Tweet")
                .tableName("Tweet")
                .partitionKey(
                        Attribute.builder().name("id0").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        tweetTable.grantReadWriteData(postCreationHandlerFunction);


        var vpc = Vpc.Builder.create(this, "SocialAWS-VPC")
                .vpcName("SocialAWS-VPC")
                .maxAzs(1)
                .subnetConfiguration(List.of(
                        SubnetConfiguration.builder()
                                .name("SocialAWS-Public-Subnet")
                                .cidrMask(16)
                                .subnetType(SubnetType.PUBLIC)
                                .build()
                ))
                .createInternetGateway(true)
                .natGateways(0)
                .build();

        var bucketSocialAWS = Bucket.fromBucketName(this, System.getenv("S3_ARTIFACTS_BUCKET"), System.getenv("S3_ARTIFACTS_BUCKET"));

        /*var bucketSocialAWS = Bucket.Builder.create(this, System.getenv("S3_ARTIFACTS_BUCKET"))
                .bucketName(System.getenv("S3_ARTIFACTS_BUCKET"))
                .versioned(false)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();

        var bucketDeployments = new BucketDeployment(this, "socialaws-jar-deployment",
                BucketDeploymentProps.builder()
                        .sources(List.of(
                                Source.asset("../assets/socialaws.jar"),
                                Source.asset("../assets/jdk-21.0.2_linux-x64_bin.tar.gz")
                        ))
                        .destinationBucket(bucketSocialAWS)
                        .vpc(vpc)
                        .build()
        );*/

        var ec2SG = SecurityGroup.Builder.create(this, "ec2-sg")
                .securityGroupName("ec2-sg")
                .allowAllOutbound(true)
                .vpc(vpc)
                .build();


        ec2SG.addIngressRule(Peer.anyIpv4(), Port.tcp(22), "Allow SSH access from anywhere");
        ec2SG.addIngressRule(Peer.anyIpv4(), Port.tcp(80), "Allow HTTP access from anywhere");
        ec2SG.addIngressRule(Peer.anyIpv4(), Port.tcp(8080), "Allow API access from anywhere");

        var ec2KeyPairNew = KeyPair.fromKeyPairName(this, "SocialAWS-EC2-KP-New", "SocialAWS-EC2-KP-New");

//        TODO: create a role to access S3 Bucket
        var ec2 = Instance.Builder.create(this, "SocialAWS-EC2")
                .instanceType(InstanceType.of(InstanceClass.T2, InstanceSize.MICRO))
                .machineImage(
                        MachineImage.latestAmazonLinux2(
                                AmazonLinux2ImageSsmParameterProps.builder()
                                        .cpuType(AmazonLinuxCpuType.X86_64)
                                        .edition(AmazonLinuxEdition.STANDARD)
                                        .userData(UserData.custom(EC2_STARTUP_SCRIPT))
                                        .storage(AmazonLinuxStorage.GENERAL_PURPOSE)
                                        .build()
                        )
                )
                .vpc(vpc)
                .vpcSubnets(SubnetSelection.builder().subnetType(SubnetType.PUBLIC).build())
                .associatePublicIpAddress(true)
                .userData(UserData.custom(EC2_STARTUP_SCRIPT))
                .allowAllOutbound(true)
                .securityGroup(ec2SG)
                .keyPair(ec2KeyPairNew)
                .build();

        tweetTable.grantReadData(ec2);
        postCreationQueue.grantSendMessages(ec2);
        bucketSocialAWS.grantRead(ec2);

        var elasticIP = CfnEIP.Builder.create(this, "SocialAWS-EC2-IP")
                .instanceId(ec2.getInstanceId())
                .build();

        var nacl = NetworkAcl.Builder.create(this, "SocialAWS-NACL")
                .vpc(vpc)
                .networkAclName("SocialAWS-NACL")
                .build();

        nacl.addEntry(
                "Http_Inbound",
                CommonNetworkAclEntryOptions.builder()
                        .ruleNumber(100)
                        .ruleAction(Action.ALLOW)
                        .cidr(AclCidr.anyIpv4())
                        .direction(TrafficDirection.INGRESS)
                        .networkAclEntryName("Http_Inbound")
                        .traffic(AclTraffic.tcpPort(80))
                        .build()
        );

        nacl.addEntry(
                "Http_Outbound",
                CommonNetworkAclEntryOptions.builder()
                        .ruleNumber(100)
                        .ruleAction(Action.ALLOW)
                        .cidr(AclCidr.anyIpv4())
                        .direction(TrafficDirection.EGRESS)
                        .networkAclEntryName("Http_Outbound")
                        .traffic(AclTraffic.tcpPort(80))
                        .build()
        );

        nacl.addEntry(
                "SSH_Inbound",
                CommonNetworkAclEntryOptions.builder()
                        .ruleNumber(101)
                        .ruleAction(Action.ALLOW)
                        .cidr(AclCidr.anyIpv4())
                        .direction(TrafficDirection.INGRESS)
                        .networkAclEntryName("SSH_Inbound")
                        .traffic(AclTraffic.tcpPort(22))
                        .build()
        );

        nacl.addEntry(
                "SSH_Outbound",
                CommonNetworkAclEntryOptions.builder()
                        .ruleNumber(101)
                        .ruleAction(Action.ALLOW)
                        .cidr(AclCidr.anyIpv4())
                        .direction(TrafficDirection.EGRESS)
                        .networkAclEntryName("SSH_Outbound")
                        .traffic(AclTraffic.tcpPort(22))
                        .build()
        );

        nacl.addEntry(
                "Ephermal_Outbound",
                CommonNetworkAclEntryOptions.builder()
                        .ruleNumber(103)
                        .ruleAction(Action.ALLOW)
                        .cidr(AclCidr.anyIpv4())
                        .direction(TrafficDirection.EGRESS)
                        .networkAclEntryName("Ephermal_Outbound")
                        .traffic(AclTraffic.tcpPortRange(1024, 65535)) // Ephemeral Ports
                        .build()
        );
    }

    private static final String EC2_STARTUP_SCRIPT = String.format("""
            #!/bin/bash
            aws s3 cp s3://%s/jdk-21.0.2_linux-x64_bin.tar.gz /home/ec2-user
            aws s3 cp s3://%s/socialaws.jar /home/ec2-user/
            
            cd /home/ec2-user/
            tar -xzf jdk-21.0.2_linux-x64_bin.tar.gz
            
            echo "export JAVA_HOME=/home/ec2-user/jdk-21.0.2" >> ~/.bashrc
            echo "export PATH=\\$PATH:\\$JAVA_HOME/bin" >> ~/.bashrc
            
            echo "export REGION=%s" >> ~/.bashrc
            echo "export AWS_ACCOUNT_ID=%s" >> ~/.bashrc
            echo "export AWS_SOCIALAWS_SQS=%s" >> ~/.bashrc
            source ~/.bashrc
            
            nohup java -jar socialaws.jar > /dev/null 2>&1 &
            """,
            System.getenv("S3_ARTIFACTS_BUCKET"),
            System.getenv("S3_ARTIFACTS_BUCKET"),
            System.getenv("REGION"),
            System.getenv("AWS_ACCOUNT_ID"),
            System.getenv("AWS_SOCIALAWS_SQS")
    );
}

